package ai.phast.ctdynamo;

import lombok.Getter;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class that behaves like a dynamo store (a table or index) but is all in-memory.
 *
 * <p>This is class level visibility. It is not meant to be used outside of the MockTable and MockIndex classes.
 */
class InMemoryDynamoStore {

    /**
     * We use this a lot (every call to put or get), so computing it once will be more efficient.
     */
    private static final AttributeValue BLANK_ATTRIBUTE_VALUE = AttributeValue.builder().s("").build();

    /**
     * Regex that matches a dynamo parameter.
     */
    private static final String PARAM = "[A-Za-z_][A-Za-z0-9_]*";

    /**
     * The items in our store, keyed first by the tuple of partition values (in key order, so a table's tuple always
     * has one entry and an index's may have up to four), then by sort key within that partition
     */
    @Getter
    private final HashMap<List<AttributeValue>, TreeMap<Map<String, AttributeValue>, Map<String, AttributeValue>>>
            db = new HashMap<>();

    /**
     * The name of the index this is storing. null if this is the primary index.
     */
    @Getter
    private final String indexName;

    /**
     * The partition key attributes for this table or index, in order. A table always has exactly one; an index may
     * have up to four, and we key our partitions on the whole tuple.
     */
    private final List<String> partitionKeyAttributes;

    /**
     * The optional sort key attribute for this table or index
     */
    private final String sortKeyAttribute;

    /**
     * A list of all attributes used to order objects in a partition. This means our sort attribute and the partition
     * and sort attributes of the table (if we are in an index)
     */
    private final Set<String> sortAttributes;

    /**
     * Tests whether or not two items (as maps) match in the primary index.
     */
    private final BiFunction<Map<String, AttributeValue>, Map<String, AttributeValue>, Boolean> primaryIndexTest;

    /**
     * The comparator that keeps the members sorted.
     */
    private final Comparator<Map<String, AttributeValue>> sortComparator;

    /**
     * Build a prepopulated store
     * @param index The table or index we are backing
     * @param primaryKeys The list of attribute names for the primary keys of the table. Required when index is a
     *     secondary index, may be null when it is the table itself
     * @param items Items to put into the store at start time
     */
    InMemoryDynamoStore(DynamoIndex<?, ?, ?, ?, ?, ?> index,
                        List<String> primaryKeys, Collection<Map<String, AttributeValue>> items) {
        indexName = index.getIndexName();
        partitionKeyAttributes = index.getPartitionKeyAttributes();
        sortKeyAttribute = index.getSortKeyAttribute();
        if ((primaryKeys == null) || primaryKeys.isEmpty()) {
            sortAttributes = (sortKeyAttribute == null ? Collections.emptySet()
                                                       : Collections.singleton(sortKeyAttribute));
        } else {
            sortAttributes = new HashSet<>(primaryKeys);
            sortAttributes.add(sortKeyAttribute);
        }
        Comparator<Map<String, AttributeValue>> comparator;
        if (indexName == null) {
            // Primary index
            // No need for a primary index test - our tree maps will remove equal objects.
            primaryIndexTest = null;

            // Simple comparator; just compare sort attributes if we have one, otherwise leave it null.
            comparator = sortKeyAttribute == null
                         ? (m, n) -> 0
                         : new AttributeMapComparator(sortKeyAttribute);
        } else {
            // Secondary index
            Objects.requireNonNull(primaryKeys);
            // Primary index test compares the two primary attributes
            primaryIndexTest = (a, b) -> primaryKeys.stream().allMatch(k -> a.get(k).equals(b.get(k)));
            // Complex comparator; uses the GSI's sort key, then uses the primary index partition and sort keys as a backup
            comparator = new AttributeMapComparator(sortKeyAttribute);
            for (var attribute : primaryKeys) {
                comparator = comparator.thenComparing(new AttributeMapComparator(attribute));
            }
        }
        sortComparator = comparator;
        if (items != null) {
            addAll(items, false);
        }
    }

    /**
     * Add the item to the database. If we are a secondary index and the item has a null key, we do nothing
     *
     * @param item The item to add
     * @return The item that this displaced, if any
     * @throws IllegalArgumentException If the table name in the query doesn't match this mock client
     */
    public synchronized Map<String, AttributeValue> add(Map<String, AttributeValue> item) {
        if (primaryIndexTest != null) {
            // Remove any value with the same primary keys. We do this even if the new value doesn't belong in our index.
            db.values().forEach(sortTree -> sortTree.values().removeIf(prevItem -> primaryIndexTest.apply(item, prevItem)));
        }
        var partKey = itemToPartitionKey(item);
        if (partKey == null) {
            if (primaryIndexTest == null) {
                throw new IllegalArgumentException("Item with null primary partition key: " + item);
            }
            // We're an index, and there is no partition value. We do not store the object.
            return null;
        }
        var sortKey = (sortKeyAttribute == null ? BLANK_ATTRIBUTE_VALUE : item.get(sortKeyAttribute));
        if (sortKey == null) {
            // We're an index, there is no sort key. We do not store this object.
            return null;
        }
        return db.computeIfAbsent(partKey, p -> new TreeMap<>(sortComparator)).put(itemToSortKey(item), item);
    }

    /**
     * Remove an item from the backing store. This will descend into child backing stores if an item is found
     * @param item The keys of the item to remove
     * @return The item found, or null if there was no match
     */
    public synchronized Map<String, AttributeValue> remove(Map<String, AttributeValue> item) {
        if (indexName == null) {
            var tree = db.get(itemToPartitionKey(item));
            return tree == null ? null : tree.remove(itemToSortKey(item));
        } else {
            // Delete according to primaryIndexTest, don't bother fetching the result
            db.values().forEach(sortTree -> sortTree.values().removeIf(prevItem -> primaryIndexTest.apply(item, prevItem)));
            return null;
        }
    }

    /**
     * Fetch the item from the keyToItem table.
     *
     * @param key The key to fetch
     * @return The item from the table
     */
    public synchronized Map<String, AttributeValue> getItem(Map<String, AttributeValue> key) {
        var range = db.get(itemToPartitionKey(key));
        return range == null
               ? null
               : range.get(itemToSortKey(key));
    }

    /**
     * Get a range of items from a partition
     * @param partition The partition values, in key order, as returned by getPartitionKeyAttributes()
     * @param lowSort The lower bound (or null if there is none)
     * @param lowInclusive true if sort keys equal to the lower bound should be included
     * @param highSort The higher bound (or null if there is none)
     * @param highInclusive true if sort keys equal to the higher bound should be included
     * @return The attribute value maps in the range
     */
    public Collection<Map<String, AttributeValue>> getRange(List<AttributeValue> partition,
                                                            AttributeValue lowSort, boolean lowInclusive,
                                                            AttributeValue highSort, boolean highInclusive) {

        // Build our low item. Fill in any additional sort attributes with values that will match our inclusivity
        Map<String, AttributeValue> lowItem;
        if (lowSort == null) {
            lowItem = null;
        } else {
            lowItem = new HashMap<>();
            if (sortAttributes != null) {
                sortAttributes.forEach(a -> lowItem.put(a, lowInclusive ? AttributeComparator.LOWEST_AV : AttributeComparator.HIGHEST_AV));
            }
            lowItem.put(sortKeyAttribute, lowSort);
        }

        // Build our high item. Fill in any additional sort attributes with values that will match our inclusivity
        Map<String, AttributeValue> highItem;
        if (highSort == null) {
            highItem = null;
        } else {
            highItem = new HashMap<>();
            if (sortAttributes != null) {
                sortAttributes.forEach(a -> highItem.put(a, highInclusive ? AttributeComparator.HIGHEST_AV : AttributeComparator.LOWEST_AV));
            }
            highItem.put(sortKeyAttribute, highSort);
        }

        return getRange(partition, lowItem, lowInclusive, highItem, highInclusive);
    }

    /**
     * Query our in-memory store
     * @param partition The partition values to search, in key order
     * @param lowSort The lower bound (or null if there is none)
     * @param lowInclusive true if sort keys equal to the lower bound should be included
     * @param highSort The higher bound (or null if there is none)
     * @param highInclusive true if sort keys equal to the higher bound should be included
     * @return An ordered collection of the values found by the query
     */
    public Collection<Map<String, AttributeValue>> getRange(List<AttributeValue> partition,
                                                            Map<String, AttributeValue> lowSort, boolean lowInclusive,
                                                            Map<String, AttributeValue> highSort, boolean highInclusive) {
        NavigableMap<Map<String, AttributeValue>, Map<String, AttributeValue>> tree = db.get(partition);
        if (tree == null) {
            return Collections.emptyList();
        }
        if (lowSort == null) {
            if (highSort != null) {
                tree = tree.headMap(highSort, highInclusive);
            }
        } else {
            if (highSort == null) {
                tree = tree.tailMap(lowSort, lowInclusive);
            } else {
                tree = tree.subMap(lowSort, lowInclusive, highSort, highInclusive);
            }
        }
        return tree.values();
    }

    /**
     * Add a collection of items to the table
     *
     * @param newItems The items to add
     * @param replaceOk If false, then we throw an exception if an added item replacing something already in the
     *     table or another item in the list to add
     * @throws IllegalArgumentException If replaceOk is not set and we have a replacement
     */
    public final synchronized void addAll(Collection<Map<String, AttributeValue>> newItems, boolean replaceOk) {
        if (replaceOk) {
            newItems.forEach(this::add);
        } else {
            for (var item: newItems) {
                var prevItem = add(item);
                if (prevItem != null) {
                    throw new IllegalArgumentException("Item " + item + " replaced " + prevItem);
                }
            }
        }
    }

    /**
     * Get a stream of all items in the database. A stream instead of a collection because most likely this will
     * be translated from attribute value maps into objects, and that is most efficient when it is a stream
     *
     * @return A stream of all items in the database
     */
    public synchronized Stream<Map<String, AttributeValue>> getItems() {
        return db.values().stream()
                   .flatMap(tree -> tree.values().stream());
    }

    /**
     * Convert an item to the tuple of partition values that keys its partition in our db
     * @param item The item
     * @return The partition values in order, or null if the item is missing any of them (which happens on an index
     *         whose partition attributes the item does not populate)
     */
    private List<AttributeValue> itemToPartitionKey(Map<String, AttributeValue> item) {
        var result = new ArrayList<AttributeValue>(partitionKeyAttributes.size());
        for (var attribute : partitionKeyAttributes) {
            var value = item.get(attribute);
            if (value == null) {
                return null;
            }
            result.add(value);
        }
        return result;
    }

    /**
     * Convert an item to a sort key by stripping out all irrelevant attributes
     * @param item The item
     * @return A sort key that matches the item
     */
    private Map<String, AttributeValue> itemToSortKey(Map<String, AttributeValue> item) {
        return item.entrySet().stream()
                   .filter(e -> sortAttributes.contains(e.getKey()))
                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get the size of our database (the number of items in it)
     * @return The size of our database
     */
    public int size() {
        return db.values().stream().mapToInt(TreeMap::size).sum();
    }

    /**
     * Get the comparator that orders our objects in a partition
     * @return The comparator that orders our objects in a partition
     */
    public Comparator<Map<String, AttributeValue>> getSortComparator() {
        return sortComparator;
    }
}
