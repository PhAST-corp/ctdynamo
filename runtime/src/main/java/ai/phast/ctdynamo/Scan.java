package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Build an execute a scan. This looks and acts like a builder, except that when you are done instead of calling
 * build() and getting the full object, you call invoke() and get the results of the scan
 *
 * <p>Queries and scans are returned from Dynamo in pages. This data structure comes in two forms: Synchronous and
 * asynchronous. In synchronous queries, a new page is requested after you run out of data in the iterator or stream.
 * In asynchronous queries, a new page is anychronously requested when you start reading from the previous page. If
 * your operation is fast (e.g., adding all items to a list), then there will be little speed difference between
 * the two forms. But if your operation is fast, e.g. if you do another database querie for each item in the result,
 * then asynchronous operations may be significantly faster. It may even be beneficial to set the page size to a
 * smaller value, so you will get each page sooner and be able to start working on the items.
 *
 * @param <T> The type of item to return
 */
public final class Scan<T> {

    /** The index or table we are querying */
    private final DynamoIndex<T, ?, ?> index;

    /** The maximum number of items to returned, or -1 if the number is unlimited */
    private int limit = -1;

    /** The maximum number of items per page to returned, or -1 if we want the biggest pages that dynamo will supply */
    private int pageSize = -1;

    /** true if this is asynchronous, false if it is synchronous */
    private boolean isAsync = false;

    /** The exclusive start key of the scan */
    private Map<String, AttributeValue> exclusiveStartKey;

    /**
     * Build a new scan
     * @param index The index or table we are scanning
     */
    Scan(DynamoIndex<T, ?, ?> index) {
        this.index = index;
    }

    /**
     * Select whether a query is synchronous or asynchronous. See the class description for information on the
     * difference
     * @param value true to make this an asynchronous query, false to make it synchronous
     * @return This query
     */
    public Scan<T> async(boolean value) {
        isAsync = value;
        return this;
    }

    /**
     * Set the maximum number of items to return
     *
     * <p>Note that this is not the same as the Dynamo limit value; Dynamo's limit is the maximum items to return per
     * page, this limit is the maximum total number of items to return. See {@link #pageSize(int)} for the value that
     * will set the Dynamo limit property.
     * @param value The maximum number of items to return
     * @return This query
     */
    public Scan<T> limit(int value) {
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
    public Scan<T> pageSize(int value) {
        pageSize = value;
        return this;
    }

    /**
     * Set the exclusive start key. You can get an exclusive start key from {@link IterableResult#getExclusiveStartKey()}
     * or from {@link DynamoIndex#getExclusiveStartKey(Object)}. Only values that come after the exclusive start in the
     * scan will be returned
     * @param value The exclusive start key
     * @return This query
     */
    public Scan<T> exclusiveStartKey(String value) {
        exclusiveStartKey = (value == null ? null : index.decodeExclusiveStart(value));
        return this;
    }

    /**
     * Shortcut to .invoke.stream()
     * @return A stream of the items from the scan
     */
    public Stream<T> stream() {
        return invoke().stream();
    }

    /**
     * Start the scan. This will be a single-threaded scan, that will return all items in the
     * table or index
     * @return An iterable result that can iterate or stream through the returned items
     */
    public IterableResult<T> invoke() {
        return invoke(0, 1);
    }

    /**
     * Scan the scan. This is a parallel scan, that returns a segment (subset) of the table or index being
     * scanned. That is, if you want to run four scans in parallel, they should build identical
     * scans, then each invoke with numSegments set to 4 and segment set to 0, 1, 2, or 3
     * @param segment The number of this segment
     * @param numSegments The total number of segments to run in parallel
     * @return The result of the scan
     */
    public IterableResult<T> invoke(int segment, int numSegments) {
        var builder = ScanRequest.builder()
                          .tableName(index.getTableName())
                          .segment(segment)
                          .totalSegments(numSegments);
        var indexName = index.getIndexName();
        if (indexName != null) {
            builder.indexName(indexName);
        }
        if (exclusiveStartKey != null) {
            builder.exclusiveStartKey(exclusiveStartKey);
        }
        if (pageSize <= 0) {
            if (limit >= 0) {
                builder.limit(limit);
            }
        } else {
            builder.limit(pageSize);
        }
        return new ScanResult<>(index, builder, limit, isAsync);
    }
}
