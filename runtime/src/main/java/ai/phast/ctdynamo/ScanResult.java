package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The result of a query. This is package private; the PaginatedResult class that this extends should be the class
 * presented to applications using this library.
 * @param <T> The type of item in the index
 */
class ScanResult<T> extends PagedResult<T, ScanResponse> {

    /** The builder to submit to dynamo for each page */
    private final ScanRequest.Builder scanBuilder;

    /**
     * Build a scan result
     * @param index The index or table we are querying
     * @param scanBuilder The query builder to submit for each page
     * @param limit The maximum number of items to return
     * @param prefetch true for asynchronous operation, false for synchronous
     * @param readLimit The limit of the number of items we should read, or -1 if there is no limit
     * @param pageSize The size of each read page, or -1 if we want as much as dynamo will provide
     * @param isFiltered A flag telling us whether or not the results may be filtered - that is, whether or not
     *     reads may be ignored
     */
    ScanResult(DynamoIndex<T, ?, ?> index, ScanRequest.Builder scanBuilder, int limit, boolean prefetch,
               int readLimit, int pageSize, boolean isFiltered) {
        super(index, limit, prefetch, pageSize, readLimit, isFiltered);
        this.scanBuilder = scanBuilder;
        init();
    }

    @Override
    CompletableFuture<ScanResponse> fetchNextPage(Map<String, AttributeValue> exclusiveStart, int fetchSize) {
        if (exclusiveStart != null) {
            scanBuilder.exclusiveStartKey(exclusiveStart);
        }
        if (fetchSize >= 0) {
            scanBuilder.limit(fetchSize);
        }
        var request = scanBuilder.build();
        return getIndex().getAsyncClient() == null
            ? CompletableFuture.supplyAsync(() -> getIndex().getClient().scan(request))
            : getIndex().getAsyncClient().scan(request);
    }

    @Override
    ScanResponse fetchCurrentPage(Map<String, AttributeValue> exclusiveStart, int fetchSize) {
        if (exclusiveStart != null) {
            scanBuilder.exclusiveStartKey(exclusiveStart);
        }
        if (fetchSize >= 0) {
            scanBuilder.limit(fetchSize);
        }
        var request = scanBuilder.build();
        return getIndex().getClient() == null
            ? getIndex().getAsyncClient().scan(request).join()
            : getIndex().getClient().scan(request);
    }

    @Override
    int getNumItemsRead(ScanResponse response) {
        return response.scannedCount() == null ? 0 : response.scannedCount();
    }

    @Override
    ConsumedCapacity getRawCapacity(ScanResponse response) {
        return response.consumedCapacity();
    }

    @Override
    Map<String, AttributeValue> getLastEvaluatedKey(ScanResponse response) {
        return response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
    }

    @Override
    List<Map<String, AttributeValue>> getItems(ScanResponse response) {
        return response.hasItems() ? response.items() : Collections.emptyList();
    }
}
