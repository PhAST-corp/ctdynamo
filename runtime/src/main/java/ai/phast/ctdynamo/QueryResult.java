package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The result of a query. This is package private; the PaginatedResult class that this extends should be the class
 * presented to applications using this library.
 * @param <T> The type of item in the index
 */
class QueryResult<T> extends PagedResult<T, QueryResponse> {

    /** The builder to submit to dynamo for each page */
    private final QueryRequest.Builder queryBuilder;

    /**
     * Build a query result
     * @param index The index or table we are querying
     * @param queryBuilder The query builder to submit for each page
     * @param limit The maximum number of items to return
     * @param prefetch true for asynchronous operation, false for synchronous
     * @param readLimit The limit of the number of items we should read, or -1 if there is no limit
     * @param pageSize The size of each read page, or -1 if we want as much as dynamo will provide
     * @param isFiltered A flag telling us whether or not the results may be filtered - that is, whether or not
     *     reads may be ignored
     */
    QueryResult(DynamoIndex<T, ?, ?> index, QueryRequest.Builder queryBuilder, int limit, boolean prefetch,
                int readLimit, int pageSize, boolean isFiltered) {
        super(index, limit, prefetch, pageSize, readLimit, isFiltered);
        this.queryBuilder = queryBuilder;
        init();
    }

    @Override
    CompletableFuture<QueryResponse> fetchNextPage(Map<String, AttributeValue> exclusiveStart, int fetchSize) {
        if (exclusiveStart != null) {
            queryBuilder.exclusiveStartKey(exclusiveStart);
        }
        if (fetchSize >= 0) {
            queryBuilder.limit(fetchSize);
        }
        var request = queryBuilder.build();
        return getIndex().getAsyncClient() == null
               ? CompletableFuture.supplyAsync(() -> getIndex().getClient().query(request))
               : getIndex().getAsyncClient().query(request);
    }

    @Override
    QueryResponse fetchCurrentPage(Map<String, AttributeValue> exclusiveStart, int fetchSize) {
        if (exclusiveStart != null) {
            queryBuilder.exclusiveStartKey(exclusiveStart);
        }
        if (fetchSize >= 0) {
            queryBuilder.limit(fetchSize);
        }
        var request = queryBuilder.build();
        return getIndex().getClient() == null
               ? getIndex().getAsyncClient().query(request).join()
               : getIndex().getClient().query(request);
    }

    @Override
    int getScannedCount(QueryResponse response) {
        return response.scannedCount() == null ? 0 : response.scannedCount();
    }

    @Override
    ConsumedCapacity getRawCapacity(QueryResponse response) {
        return response.consumedCapacity();
    }

    @Override
    Map<String, AttributeValue> getLastEvaluatedKey(QueryResponse response) {
        return response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
    }

    @Override
    List<Map<String, AttributeValue>> getItems(QueryResponse response) {
        return response.hasItems() ? response.items() : Collections.emptyList();
    }
}
