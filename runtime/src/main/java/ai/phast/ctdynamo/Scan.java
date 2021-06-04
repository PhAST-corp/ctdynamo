package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

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
public final class Scan<T> extends BaseQueryScan<T, DynamoIndex<T, ?, ?>, Scan<T>> {

    /**
     * Build a new scan
     * @param index The index or table we are scanning
     */
    Scan(DynamoIndex<T, ?, ?> index) {
        super(index);
    }

    @Override
    protected Scan<T> self() {
        return this;
    }

    /**
     * Start the scan. This will be a single-threaded scan, that will return all items in the
     * table or index
     * @return An iterable result that can iterate or stream through the returned items
     */
    @Override
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
        var index = getIndex();
        var builder = ScanRequest.builder()
                          .tableName(index.getTableName())
                          .consistentRead(isConsistentRead())
                          .segment(segment)
                          .totalSegments(numSegments);
        if (getFilterExpression() != null) {
            builder.filterExpression(getFilterExpression())
                .expressionAttributeNames(getAttributeNames())
                .expressionAttributeValues(getValues());
        }
        var indexName = index.getIndexName();
        if (indexName != null) {
            builder.indexName(indexName);
        }
        if (getExclusiveStartKey() != null) {
            builder.exclusiveStartKey(getExclusiveStartKey());
        }
        var pageSize = getPageSize();
        if (pageSize > 0) {
            builder.limit(pageSize);
        }
        return new ScanResult<>(index, builder, getLimit(), isAsync());
    }
}
