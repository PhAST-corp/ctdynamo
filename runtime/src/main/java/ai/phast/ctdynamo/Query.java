package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.Select;

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
public final class Query<T, PartitionT, SortT> extends BaseQueryScan<T, DynamoIndex<T, PartitionT, SortT>, Query<T, PartitionT, SortT>> {

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
    Query(DynamoIndex<T, PartitionT, SortT> index) {
        super(index);
        var partitionKeyAttribute = index.getPartitionKeyAttribute();
        getAttributeNames().put("#" + partitionKeyAttribute, partitionKeyAttribute);
    }

    /**
     * Set the partition value that will be scanned by this query
     * @param partitionValue The partition value
     * @return This query
     */
    public Query<T, PartitionT, SortT> partitionValue(PartitionT partitionValue) {
        getValues().put(":ctdynamo_p", getIndex().partitionValueToAttributeValue(partitionValue));
        return this;
    }

    /**
     * Query between the lo and hi values, inclusive. The partition key of the lo value sets the partition searched
     * @param lo The lowest sort value to include
     * @param hi The highest sort value to include
     * @return This query
     */
    public Query<T, PartitionT, SortT> between(T lo, T hi) {
        partitionValue(getIndex().getPartitionValue(lo));
        return sortBetween(getIndex().getSortValue(lo), getIndex().getSortValue(hi));
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
        keyExpression = "#" + getIndex().getPartitionKeyAttribute() + " = :ctdynamo_p AND #" + getIndex().getSortKeyAttribute()
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
        partitionValue(getIndex().getPartitionValue(bound));
        return sortGreaterThan(getIndex().getSortValue(bound));
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
        partitionValue(getIndex().getPartitionValue(bound));
        return sortGreaterThanOrEqual(getIndex().getSortValue(bound));
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
        partitionValue(getIndex().getPartitionValue(bound));
        return sortLessThan(getIndex().getSortValue(bound));
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
        partitionValue(getIndex().getPartitionValue(bound));
        return sortLessThanOrEqual(getIndex().getSortValue(bound));
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
        keyExpression = "#" + getIndex().getPartitionKeyAttribute() + " = :ctdynamo_p AND begins_with(#"
                            + getIndex().getSortKeyAttribute() + ", :ctdynamo_s1)";
        return this;
    }

    /**
     * Build a sort key relational expression
     * @param operator The relational operator to use
     * @return A string for the required expression
     */
    private String buildKeyExpression(String operator) {
        return "#" + getIndex().getPartitionKeyAttribute() + " = :ctdynamo_p AND #" + getIndex().getSortKeyAttribute()
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

    @Override
    protected Query<T, PartitionT, SortT> self() {
        return this;
    }

    /**
     * Start the query
     * @return An iterable result that can iterate or stream through the returned items
     * @throws IllegalStateException If no partition key has been set
     */
    @Override
    public IterableResult<T> invoke() {
        if (getValues().get(":ctdynamo_p") == null) {
            throw new IllegalStateException("The partition key must be set before calling query.invoke()");
        }
        var index = getIndex();
        var builder = QueryRequest.builder()
            .tableName(index.getTableName())
            .consistentRead(isConsistentRead())
            .filterExpression(getFilterExpression())
            .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES);
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
            getAttributeNames().put("#" + sortAttribute, sortAttribute);
            getValues().put(":ctdynamo_s1", index.sortValueToAttributeValue(sort1));
            if (sort2 != null) {
                getValues().put(":ctdynamo_s2", index.sortValueToAttributeValue(sort2));
            }
        }
        builder.scanIndexForward(scanForward);
        if (getExclusiveStartKey() != null) {
            builder.exclusiveStartKey(getExclusiveStartKey());
        }
        builder.expressionAttributeValues(getValues())
            .expressionAttributeNames(getAttributeNames());
        var pageSize = getPageSize();
        if (pageSize > 0) {
            builder.limit(pageSize);
        }
        return new QueryResult<>(index, builder, getLimit(), isAsync());
    }
}
