package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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
 * @param <PartitionT> The type of the partition key
 * @param <SortT> The type of the sort key
 */
public final class Query<T, PartitionT, SortT> {

    /** All expression attribute values */
    private final Map<String, AttributeValue> values = new HashMap<>();

    /** All attribute names used in expressions */
    private final Map<String, String> attributeNames = new HashMap<>();

    /** The index or table we are querying */
    private final DynamoIndex<T, PartitionT, SortT> index;

    /** The key expression string */
    private String keyExpression = null;

    /**
     * The sort key(s). If only one key is needed, sort2 will be null; if only a partition key is used, sort1 and sort2
     * will both be null
     */
    private SortT sort1, sort2;

    /** True if we scan from low to high, false if we scan from high to low */
    private boolean scanForward = true;

    /** The exclusive start key of the query */
    private Map<String, AttributeValue> startKey;

    /** The maximum number of items to return, or -1 if the number is unlimited */
    private int limit = -1;

    /** The maximum number of items per page to return, or -1 if we want the biggest pages that dynamo will supply */
    private int pageSize = -1;

    /** true if this is asynchronous, false if it is synchronous */
    private boolean isAsync;

    /**
     * Build a new query
     * @param index The index or table we are querying
     */
    Query(DynamoIndex<T, PartitionT, SortT> index) {
        this.index = index;
        var partitionKeyAttribute = index.getPartitionKeyAttribute();
        attributeNames.put("#" + partitionKeyAttribute, partitionKeyAttribute);
    }

    /**
     * Select whether a query is synchronous or asynchronous. See the class description for information on the
     * difference
     * @param value true to make this an asynchronous query, false to make it synchronous
     * @return This query
     */
    public Query<T, PartitionT, SortT> async(boolean value) {
        isAsync = value;
        return this;
    }

    /**
     * Set the partition value that will be scanned by this query
     * @param partitionValue The partition value
     * @return This query
     */
    public Query<T, PartitionT, SortT> partitionValue(PartitionT partitionValue) {
        values.put(":ctdynamo_p", index.partitionValueToAttributeValue(partitionValue));
        return this;
    }

    /**
     * Query between the lo and hi values, inclusive. The partition key of the lo value sets the partition searched
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, PartitionT, SortT> between(T lo, T hi) {
        partitionValue(index.getPartitionValue(lo));
        return sortBetween(index.getSortValue(lo), index.getSortValue(hi));
    }

    /**
     * Query between the lo and hi values, inclusive. The lo partition value will set the partition searched
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, PartitionT, SortT> between(Key<PartitionT, SortT> lo, Key<PartitionT, SortT> hi) {
        partitionValue(lo.getPartition());
        return sortBetween(lo.getSort(), hi.getSort());
    }

    /**
     * Query between the lo and hi values, inclusive
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortBetween(SortT lo, SortT hi) {
        keyExpression = "#" + index.getPartitionKeyAttribute() + " = :ctdynamo_p AND #" + index.getSortKeyAttribute()
                            + " BETWEEN :ctdynamo_s1 AND :ctdynamo_s2";
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
    public Query<T, PartitionT, SortT> greaterThan(T bound) {
        partitionValue(index.getPartitionValue(bound));
        return sortGreaterThan(index.getSortValue(bound));
    }

    /**
     * Query values greater than the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> greaterThan(Key<PartitionT, SortT> bound) {
        partitionValue(bound.getPartition());
        return sortGreaterThan(bound.getSort());
    }

    /**
     * Query values greater than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortGreaterThan(SortT bound) {
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
    public Query<T, PartitionT, SortT> greaterThanOrEqual(T bound) {
        partitionValue(index.getPartitionValue(bound));
        return sortGreaterThanOrEqual(index.getSortValue(bound));
    }

    /**
     * Query values greater than or equal to the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above or equal to this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> greaterThanOrEqual(Key<PartitionT, SortT> bound) {
        partitionValue(bound.getPartition());
        return sortGreaterThanOrEqual(bound.getSort());
    }

    /**
     * Query values greater than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys above or equal to this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortGreaterThanOrEqual(SortT bound) {
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
    public Query<T, PartitionT, SortT> lessThan(T bound) {
        partitionValue(index.getPartitionValue(bound));
        return sortLessThan(index.getSortValue(bound));
    }

    /**
     * Query values less than the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> lessThan(Key<PartitionT, SortT> bound) {
        partitionValue(bound.getPartition());
        return sortLessThan(bound.getSort());
    }

    /**
     * Query values less than the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortLessThan(SortT bound) {
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
    public Query<T, PartitionT, SortT> lessThanOrEqual(T bound) {
        partitionValue(index.getPartitionValue(bound));
        return sortLessThanOrEqual(index.getSortValue(bound));
    }

    /**
     * Query values less than or equal to the given bound. The bound's partition value sets the partition searched.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than or equal to this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> lessThanOrEqual(Key<PartitionT, SortT> bound) {
        partitionValue(bound.getPartition());
        return sortLessThanOrEqual(bound.getSort());
    }

    /**
     * Query values less than or equal to the given bound.
     * This clears any previous sort key restriction
     * @param bound Only return values with sort keys less than or equal to this bound
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortLessThanOrEqual(SortT bound) {
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
    public Query<T, PartitionT, SortT> startsWith(Key<PartitionT, SortT> prefix) {
        partitionValue(prefix.getPartition());
        return sortStartsWith(prefix.getSort());
    }

    /**
     * Query values whose sort key begins with the given prefix. This is only usable on sort key types that are stored
     * as strings in Dynamo.
     * This clears any previous sort key restriction
     * @param prefix Only return values with sort keys that start with the given prefix
     * @return This query
     */
    public Query<T, PartitionT, SortT> sortStartsWith(SortT prefix) {
        sort1 = prefix;
        sort2 = null;
        keyExpression = "#" + index.getPartitionKeyAttribute() + " = :ctdynamo_p AND begins_with(#"
                            + index.getSortKeyAttribute() + ", :ctdynamo_s1)";
        return this;
    }

    /**
     * Build a sort key relational expression
     * @param operator The relational operator to use
     * @return A string for the required expression
     */
    private String buildKeyExpression(String operator) {
        return "#" + index.getPartitionKeyAttribute() + " = :ctdynamo_p AND #" + index.getSortKeyAttribute()
                   + operator + ":ctdynamo_s1";
    }

    /**
     * Set whether we scan forward (low to high) or backward (high to low)
     * @param value If true, scan forward. If false, scan backward
     * @return This query
     */
    public Query<T, PartitionT, SortT> scanForward(boolean value) {
        scanForward = value;
        return this;
    }

    /**
     * Set the maximum number of items to return. After this many items are returned, the iterator or stream will
     * stop, and the {@link IterableResult#getExclusiveStartKey()} value will return the object that will continue.
     *
     * <p>Note that this is not the same as the Dynamo limit value; Dynamo's limit is the maximum items to return per
     * page, this limit is the maximum total number of items to return. See {@link #pageSize(int)} for the value that
     * will set the Dynamo limit property.
     * @param value The maximum number of items to return
     * @return This query
     */
    public Query<T, PartitionT, SortT> limit(int value) {
        limit = value;
        return this;
    }

    /**
     * Set the page size. If this is not set, it will default to the same as the limit if there is no filter
     * expression; if there is a filter expression, it will default to double the limit. If there is no limit and no
     * page size, then the page size is limited only by Dynamo. For synchronous queries, bigger
     * limits will have better performance. For asynchronous queries, you may get better performance from smaller
     * queries because you will get your first results back sooner.
     * @param value The page size
     * @return This query
     */
    public Query<T, PartitionT, SortT> pageSize(int value) {
        pageSize = value;
        return this;
    }

    /**
     * Set the exclusive start key. You can get an exclusive start key from {@link IterableResult#getExclusiveStartKey()}
     * or from {@link DynamoIndex#getExclusiveStartKey(Object)}. Only values that come after the exclusive start in the
     * query will be returned. In that way, it is similar to changing the low/high bound, but it is more precise because
     * when multiple items in an index have the same sort key the exclusive start can resume exactly where the prior
     * query left off
     * @param value The exclusive start key
     * @return This query
     */
    public Query<T, PartitionT, SortT> exclusiveStartKey(String value) {
        startKey = (value == null ? null : index.decodeExclusiveStart(value));
        return this;
    }

    /**
     * Shortcut to .invoke.stream()
     * @return A stream of the items from the query
     */
    public Stream<T> stream() {
        return invoke().stream();
    }

    /**
     * Start the query
     * @return An iterable result that can iterate or stream through the returned items
     * @throws IllegalStateException If no partition key has been set
     */
    public IterableResult<T> invoke() {
        if (values.get(":ctdynamo_p") == null) {
            throw new IllegalStateException("The partition key must be set before calling query.invoke()");
        }
        var builder = QueryRequest.builder()
            .tableName(index.getTableName());
        var indexName = index.getIndexName();
        if (indexName != null) {
            builder.indexName(indexName).select(Select.ALL_ATTRIBUTES);
        }
        if (keyExpression == null) {
            // Default, just the partition
            builder.keyConditionExpression("#" + index.getPartitionKeyAttribute() + " = :ctdynamo_p");
        } else {
            builder.keyConditionExpression(keyExpression);
            var sortAttribute = index.getSortKeyAttribute();
            attributeNames.put("#" + sortAttribute, sortAttribute);
            values.put(":ctdynamo_s1", index.sortValueToAttributeValue(sort1));
            if (sort2 != null) {
                values.put(":ctdynamo_s2", index.sortValueToAttributeValue(sort2));
            }
        }
        builder.scanIndexForward(scanForward);
        if (startKey != null) {
            builder.exclusiveStartKey(startKey);
        }
        builder.expressionAttributeValues(values)
            .expressionAttributeNames(attributeNames);
        if (pageSize <= 0) {
            if (limit >= 0) {
                builder.limit(limit);
            }
        } else {
            builder.limit(pageSize);
        }
        return new QueryResult<>(index, builder, limit, isAsync);
    }
}
