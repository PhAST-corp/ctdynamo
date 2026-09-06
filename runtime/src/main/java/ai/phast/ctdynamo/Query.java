package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.HashMap;

/**
 * Build an execute a query. This looks and acts like a builder, except that when you are done instead of calling
 * build() and getting the full object, you call invoke() and get the results of the query
 *
 * <p>Queries and scans are returned from Dynamo in pages. This data structure comes in two forms: Synchronous and
 * asynchronous. In synchronous queries, a new page is requested after you run out of data in the iterator or stream.
 * In asynchronous queries, a new page is asynchronously requested when you start reading from the previous page. If
 * your operation is fast (e.g., adding all items to a list), then there will be little speed difference between
 * the two forms. But if your operation is slow, e.g. if you do another database query for each item in the result,
 * then asynchronous operations may be significantly faster. It may even be beneficial to set the page size to a
 * smaller value, so you will get each page sooner and be able to start working on the items.
 *
 * @param <T> The type of item to return
 * @param <Partition1T> The type of the first partition key
 * @param <Partition2T> The type of the second partition key, or Void if the index has fewer than two
 * @param <Partition3T> The type of the third partition key, or Void if the index has fewer than three
 * @param <Partition4T> The type of the fourth partition key, or Void if the index has fewer than four
 * @param <SortT> The type of the sort key
 */
public final class Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT>
        extends BaseQueryScan<T, DynamoIndex<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT>,
        Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT>> {

    /** The key expression string */
    private String keyExpression = null;

    /**
     * The sort key(s). If only one key is needed, sort2 will be null; if only a partition key is used, sort1 and sort2
     * will both be null
     */
    private SortT sort1, sort2;

    /** True if we scan from low to high, false if we scan from high to low */
    private boolean scanForward = true;

    /**
     * Build a new query
     * @param index The index or table we are querying
     */
    Query(DynamoIndex<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> index) {
        super(index);
        for (var partitionKeyAttribute : index.getPartitionKeyAttributes()) {
            getAttributeNames().put("#" + partitionKeyAttribute, partitionKeyAttribute);
        }
    }

    /**
     * Set the partition values that will be scanned by this query, reading them from an item
     * @param item The item to read the partition values from
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> partitionItem(T item) {
        var index = getIndex();
        return partitionValue(index.getPartitionValue1(item), index.getPartitionValue2(item),
                index.getPartitionValue3(item), index.getPartitionValue4(item));
    }

    /**
     * Set the partition values that will be scanned by this query, reading them from a key
     * @param key The key to read the partition values from
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> partitionKey(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> key) {
        return partitionValue(key.getPartition1(), key.getPartition2(), key.getPartition3(), key.getPartition4());
    }

    /**
     * Set the partition value that will be scanned by this query
     * @param partition1Value The first partition value
     * @param partition2Value The second partition value, or null if the index has only one partition key
     * @param partition3Value The third partition value, or null if the index has fewer than three partition keys
     * @param partition4Value The fourth partition value, or null if the index has fewer than four partition keys
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> partitionValue(
            Partition1T partition1Value, Partition2T partition2Value, Partition3T partition3Value,
            Partition4T partition4Value) {
        var index = getIndex();
        var numPartitionsExpected = index.getPartitionKeyAttributes().size();
        // We do some checks, but we skip redundant checks the index does alreadcy. E.g., we don't bother confirming
        // that non-null partition values should be there if we're going to call a valueToAttributeValue function
        // anyway, because it will raise an exception if this doesn't make sense.
        getValues().put(":ctdynamo_p1", index.partitionValue1ToAttributeValue(partition1Value));
        if (partition2Value == null) {
            if (numPartitionsExpected != 1) {
                throw new IllegalArgumentException("Expected " + numPartitionsExpected + " partition keys, got 1");
            }
            if (partition3Value != null || partition4Value != null) {
                throw new IllegalArgumentException("Got a null partition 2, then non-null 3 or 4");
            }
        } else {
            getValues().put(":ctdynamo_p2", index.partitionValue2ToAttributeValue(partition2Value));
            if (partition3Value == null) {
                if (numPartitionsExpected != 2) {
                    throw new IllegalArgumentException("Expected " + numPartitionsExpected + " partition keys, got 2");
                }
                if (partition4Value != null) {
                    throw new IllegalArgumentException("Got a null partition 3, then non-null 4");
                }
            } else {
                getValues().put(":ctdynamo_p3", index.partitionValue3ToAttributeValue(partition3Value));
                if (partition4Value == null) {
                    if (numPartitionsExpected != 3) {
                        throw new IllegalArgumentException("Expected " + numPartitionsExpected + " partition keys, got 3");
                    }
                } else {
                    getValues().put(":ctdynamo_p4", index.partitionValue4ToAttributeValue(partition4Value));
                }
            }
        }
        return this;
    }

    /**
     * Query values whose sort key equals the given value.
     * This clears any previous sort key restriction
     * @param value Only return values with this exact sort key
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortEquals(SortT value) {
        sort1 = value;
        sort2 = null;
        keyExpression = buildKeyExpression("=");
        return this;
    }

    /**
     * Query between the lo and hi values, inclusive. The partition key of the lo value sets the partition searched
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> between(T lo, T hi) {
        var index = getIndex();
        partitionItem(lo);
        return sortBetween(index.getSortValue(lo), index.getSortValue(hi));
    }

    /**
     * Query between the lo and hi values, inclusive. The lo partition value will set the partition searched
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> between(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> lo,
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> hi) {
        partitionKey(lo);
        return sortBetween(lo.getSort(), hi.getSort());
    }

    /**
     * Query between the lo and hi values, inclusive
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortBetween(SortT lo, SortT hi) {
        keyExpression = buildPartitionExpression()
                .append(" AND #").append(getIndex().getSortKeyAttribute())
                .append(" BETWEEN :ctdynamo_s1 AND :ctdynamo_s2")
                .toString();
        sort1 = lo;
        sort2 = hi;
        return this;
    }

    /**
     * Query values greater than the given bound. The partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> greaterThan(T bound) {
        partitionItem(bound);
        return sortGreaterThan(getIndex().getSortValue(bound));
    }

    /**
     * Query values greater than the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> greaterThan(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> bound) {
        partitionKey(bound);
        return sortGreaterThan(bound.getSort());
    }

    /**
     * Query values greater than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortGreaterThan(SortT bound) {
        sort1 = bound;
        sort2 = null;
        keyExpression = buildKeyExpression(">");
        return this;
    }

    /**
     * Query values greater than or equal to the given bound. The partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> greaterThanOrEqual(T bound) {
        partitionItem(bound);
        return sortGreaterThanOrEqual(getIndex().getSortValue(bound));
    }

    /**
     * Query values greater than or equal to the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> greaterThanOrEqual(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> bound) {
        partitionKey(bound);
        return sortGreaterThanOrEqual(bound.getSort());
    }

    /**
     * Query values greater than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortGreaterThanOrEqual(SortT bound) {
        sort1 = bound;
        sort2 = null;
        keyExpression = buildKeyExpression(">=");
        return this;
    }

    /**
     * Query values less than the given bound. The partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> lessThan(T bound) {
        partitionItem(bound);
        return sortLessThan(getIndex().getSortValue(bound));
    }

    /**
     * Query values less than the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> lessThan(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> bound) {
        partitionKey(bound);
        return sortLessThan(bound.getSort());
    }

    /**
     * Query values less than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortLessThan(SortT bound) {
        sort1 = bound;
        sort2 = null;
        keyExpression = buildKeyExpression("<");
        return this;
    }

    /**
     * Query values less than or equal to the given bound. The partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys below or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> lessThanOrEqual(T bound) {
        partitionItem(bound);
        return sortLessThanOrEqual(getIndex().getSortValue(bound));
    }

    /**
     * Query values less than or equal to the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> lessThanOrEqual(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> bound) {
        partitionKey(bound);
        return sortLessThanOrEqual(bound.getSort());
    }

    /**
     * Query values less than or equal to the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than or equal to this bound
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortLessThanOrEqual(SortT bound) {
        sort1 = bound;
        sort2 = null;
        keyExpression = buildKeyExpression("<=");
        return this;
    }

    /**
     * Query values whose sort key begins with the given prefix. This is only usable on sort key types that are stored
     * as strings in Dynamo.
     * This clears any previous sort key restriction
     * @param prefix Only return values with sort keys that start with the given prefix
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> startsWith(
            Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> prefix) {
        partitionKey(prefix);
        return sortStartsWith(prefix.getSort());
    }

    /**
     * Query values whose sort key begins with the given prefix. This is only usable on sort key types that are stored
     * as strings in Dynamo.
     * This clears any previous sort key restriction
     * @param prefix Only return values with sort keys that start with the given prefix
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> sortStartsWith(SortT prefix) {
        sort1 = prefix;
        sort2 = null;
        keyExpression = buildPartitionExpression()
                .append(" AND begins_with(#").append(getIndex().getSortKeyAttribute())
                .append(", :ctdynamo_s1)").toString();
        return this;
    }

    /**
     * Clears any sort value previously set on this query.
     * If this is called, the query will be returning the entire partition (unless there is a condition expression).
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> clearSort() {
        sort1 = null;
        sort2 = null;
        keyExpression = null;

        return this;
    }

    /**
     * Build a sort key relational expression
     * @param operator The relational operator to use
     * @return A string for the required expression
     */
    private String buildKeyExpression(String operator) {
        var expression = buildPartitionExpression();
        if (operator != null) {
            expression.append(" AND #").append(getIndex().getSortKeyAttribute()).append(operator).append(":ctdynamo_s1");
        }
        return expression.toString();
    }

    /**
     * Set whether we scan forward (low to high) or backward (high to low)
     * @param value If true, scan forward. If false, scan backward
     * @return This query
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> scanForward(boolean value) {
        scanForward = value;
        return this;
    }

    @Override
    protected Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> self() {
        return this;
    }

    /**
     * Start the query
     * @return An iterable result that can iterate or stream through the returned items
     * @throws IllegalStateException If no partition key has been set
     */
    @Override
    public IterableResult<T> invoke() {
        if (getValues().get(":ctdynamo_p1") == null) {
            throw new IllegalStateException("The partition key must be set before calling query.invoke()");
        }
        var index = getIndex();
        var builder = QueryRequest.builder()
            .tableName(index.getTableName())
            .consistentRead(isConsistentRead())
            .filterExpression(getFilterExpression())
            .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
        // Copy the values and attributes map so we don't modify the query object, in case it will be reused
        var valuesCopy = new HashMap<>(getValues());
        var attributesCopy = new HashMap<>(getAttributeNames());
        var indexName = index.getIndexName();
        if (indexName != null) {
            builder.indexName(indexName).select(Select.ALL_PROJECTED_ATTRIBUTES);
        }
        if (keyExpression == null) {
            // Default, just the partition
            builder.keyConditionExpression(buildPartitionExpression().toString());
        } else {
            builder.keyConditionExpression(keyExpression);
            var sortAttribute = index.getSortKeyAttribute();
            attributesCopy.put("#" + sortAttribute, sortAttribute);
            valuesCopy.put(":ctdynamo_s1", index.sortValueToAttributeValue(sort1));
            if (sort2 != null) {
                valuesCopy.put(":ctdynamo_s2", index.sortValueToAttributeValue(sort2));
            }
        }
        builder.scanIndexForward(scanForward);
        if (getExclusiveStartKey() != null) {
            builder.exclusiveStartKey(getExclusiveStartKey());
        }
        builder.expressionAttributeValues(valuesCopy)
            .expressionAttributeNames(attributesCopy);
        return new QueryResult<>(index, builder, getLimit(), isAsync(), getReadLimit(),
            getPageSize(), getFilterExpression() != null);
    }

    /**
     * Make a string builder, put the partition key expression into it
     * @return The string builder
     */
    private StringBuilder buildPartitionExpression() {
        var attributes = getIndex().getPartitionKeyAttributes();
        var numAttributes = attributes.size();
        var sb = new StringBuilder();
        sb.append('#').append(attributes.get(0)).append(" = :ctdynamo_p1");
        for (var i = 1; i < numAttributes; i++) {
            sb.append(" AND #").append(attributes.get(i)).append(" = :ctdynamo_p").append(i + 1);
        }
        return sb;
    }
}
