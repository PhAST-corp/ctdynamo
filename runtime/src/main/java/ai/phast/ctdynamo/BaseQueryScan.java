package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class to set up a query or scan. This handles all common parameters between the two.
 *
 * @param <T> The type of item to return
 * @param <IndexT> The index type we use
 * @param <ThisT> The type returned by fluent functions
 */
abstract class BaseQueryScan<T, IndexT extends DynamoIndex<T, ?, ?>, ThisT extends BaseQueryScan<T, IndexT, ThisT>> {

    /** All expression attribute values */
    private final Map<String, AttributeValue> values = new HashMap<>();

    /** All attribute names used in expressions */
    private final Map<String, String> attributeNames = new HashMap<>();

    /** The index or table we are reading */
    private final IndexT index;

    /** The exclusive start key of the read */
    private Map<String, AttributeValue> startKey;

    /** The filter expression to use when searching */
    private String filterExpression;

    /** The maximum number of items to return, or -1 if the number is unlimited */
    private int limit = -1;

    /** The maximum number of items per page to return, or -1 if we want the biggest pages that dynamo will supply */
    private int pageSize = -1;

    /** true if this is asynchronous, false if it is synchronous */
    private boolean isAsync;

    /** true to use consistent reads. False or null to use neither. */
    private Boolean isConsistentRead;

    /**
     * Build a new query or scan
     * @param index The index or table we are reading
     */
    BaseQueryScan(IndexT index) {
        this.index = index;
    }

    /**
     * Select whether a operation is synchronous or asynchronous. See the class description for information on the
     * difference
     * @param value true to make this an asynchronous read, false to make it synchronous
     * @return This query or scan
     */
    public ThisT async(boolean value) {
        isAsync = value;
        return self();
    }

    /**
     * Set whether or not this operation should use consistent reads. "false" is the default.
     * @param value true for consistent reads
     * @return This query or scan
     */
    public ThisT consistentRead(boolean value) {
        isConsistentRead = value;
        return self();
    }

    /**
     * Set the maximum number of items to return. After this many items are returned, the iterator or stream will
     * stop, and the {@link IterableResult#getExclusiveStartKey()} value will return the object that will continue.
     *
     * <p>Note that this is not the same as the Dynamo limit value; Dynamo's limit is the maximum items to return per
     * page, this limit is the maximum total number of items to return. See {@link #pageSize(int)} for the value that
     * will set the Dynamo limit property.
     * @param value The maximum number of items to return
     * @return This query or scan
     */
    public ThisT limit(int value) {
        limit = value;
        return self();
    }

    /**
     * Set the page size. If this is not set, it will default to the same as the limit if there is no filter
     * expression; if there is a filter expression, it will default to double the limit. If there is no limit and no
     * page size, then the page size is limited only by Dynamo. For synchronous queries, bigger
     * limits will have better performance. For asynchronous queries, you may get better performance from smaller
     * queries because you will get your first results back sooner.
     * @param value The page size
     * @return This query or scan
     */
    public ThisT pageSize(int value) {
        pageSize = value;
        return self();
    }

    /**
     * Set the exclusive start key. You can get an exclusive start key from {@link IterableResult#getExclusiveStartKey()}
     * or from {@link DynamoIndex#getExclusiveStartKey(Object)}. Only values that come after the exclusive start in the
     * query or scan will be returned. In that way, it is similar to changing the low/high bound, but it is more precise because
     * when multiple items in an index have the same sort key the exclusive start can resume exactly where the prior
     * query or scan left off
     * @param value The exclusive start key
     * @return This query or scan
     */
    public ThisT exclusiveStartKey(String value) {
        startKey = (value == null ? null : index.decodeExclusiveStart(value));
        return self();
    }

    /**
     * Add a filter expression. Only results that match the expression will be returned.
     * @param expression A filter expression
     * @return This scan or query or scan
     */
    public ThisT filter(ConditionExpression expression) {
        filterExpression = expression.getExpression();
        var exprAttributes = expression.getAttributeNames();
        if (exprAttributes != null) {
            attributeNames.putAll(exprAttributes);
        }
        var exprValues = expression.getValues();
        if (exprValues != null) {
            values.putAll(exprValues);
        }
        return self();
    }

    /**
     * Shortcut to .invoke.stream()
     * @return A stream of the items from the query or scan
     */
    public final Stream<T> stream() {
        return invoke().stream();
    }

    /**
     * Return this, cast to the customer-visible type. Needed to support the fluent API.
     * @return This, cast to the customer-visible type.
     */
    abstract ThisT self();

    /**
     * Start the operation
     * @return An iterable result that can iterate or stream through the returned items
     */
    public abstract IterableResult<T> invoke();

    /**
     * Get the limit of how many total items to return
     * @return The limit
     */
    final int getLimit() {
        return limit;
    }

    /**
     * Get the page size to use. If the page size has not been set, but the limit has, then the page size will be
     * based on the limit.
     * @return The page size to use
     */
    final int getPageSize() {
        if ((pageSize > 0) || (limit < 0)) {
            return pageSize;
        } else {
            return filterExpression == null ? limit : limit * 2;
        }
    }

    /**
     * Get the exclusive start key
     * @return The exclusive start key
     */
    final Map<String, AttributeValue> getExclusiveStartKey() {
        return startKey;
    }

    /**
     * Is this an async operation?
     * @return true if this is an async operation, false if it is synchronous
     */
    final boolean isAsync() {
        return isAsync;
    }

    /**
     * Does this use consistent reads?
     * @return true if this uses consistent reads, false if not
     */
    final Boolean isConsistentRead() {
        return isConsistentRead;
    }

    /**
     * Get the index object we are reading
     * @return Our index
     */
    public final IndexT getIndex() {
        return index;
    }

    /**
     * Get expression attribute names
     * @return The map of expression attribute names
     */
    final Map<String, String> getAttributeNames() {
        return attributeNames;
    }

    /**
     * Get expression attribute values
     * @return The map of expression attribute values
     */
    final Map<String, AttributeValue> getValues() {
        return values;
    }

    /**
     * Get our filter expression if any
     * @return Our filter expression or null if there is none
     */
    final String getFilterExpression() {
        return filterExpression;
    }
}
