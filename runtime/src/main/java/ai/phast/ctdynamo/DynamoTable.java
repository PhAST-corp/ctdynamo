package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The base class for all dynamo tables. You usually would not want to extend this yourself. Instead, if you annotate
 * a class with @{@link ai.phast.ctdynamo.annotations.DynamoItem}, then the dynamo annotation processor will at compile
 * time create a class that extends {@link DynamoTable} and stores the annotated class.
 * @param <T> The type of item to store in the table
 * @param <PartitionT> The type of the partition key for this table
 * @param <SortT> The type of the sort key for this table
 */
public abstract class DynamoTable<T, PartitionT, SortT> extends DynamoIndex<T, PartitionT, SortT> {

    /**
     * Dynamo will fail if we try to do more than this many operations in one batch
     */
    private static final int MAX_ITEMS_PER_BATCH = 25;

    /**
     * Construct a new dynamo table.
     * @param client Our dynamo client (may be null)
     * @param asyncClient Our async dynamo client (may be null)
     * @param tableName The name of this table
     * @param partitionKeyAttribute The name of our partition key attribute
     * @param sortKeyAttribute The name of our sort key attribute
     * @throws NullPointerException If both client and asyncClient are null
     */
    public DynamoTable(DynamoDbClient client, DynamoDbAsyncClient asyncClient, String tableName,
                       String partitionKeyAttribute, String sortKeyAttribute) {
        super(client, asyncClient, tableName, null, partitionKeyAttribute, sortKeyAttribute);
    }

    /**
     * Read an item from the table
     * @param value The partition and sort keys will be read from this item and used to fetch the result
     * @return The item stored in the table with partition and sort keys that match value, or null if no such item
     *         exists
     */
    public final T getItem(T value) {
        return getItem(getPartitionValue(value), getSortValue(value));
    }

    /**
     * Read an item from the table
     * @param key A key holding the partition and sort keys
     * @return The item stored in the table with the specified partition and sort keys, or null if no such item
     *         exists
     */
    public final T getItem(Key<PartitionT, SortT> key) {
        return getItem(key.getPartition(), key.getSort());
    }

    /**
     * Read an item from the table
     * @param partitionValue The partition value to use
     * @param sortValue The sort value to use, or null if this table has no sort key
     * @return The item stored in the table that has the matching partition and sort keys, or null if no such item exists
     */
    public T getItem(PartitionT partitionValue, SortT sortValue) {
        var response = rawGetItem(GetItemRequest.builder()
                               .tableName(getTableName())
                               .key(keysToMap(partitionValue, sortValue))
                               .build());
        return response.hasItem() ? decode(response.item()) : null;
    }

    /**
     * Read an item from the table. Capacity data will be returned, and consistent read may be used
     * @param value The partition and sort keys will be read from this item and used to fetch the result
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A result that contains the matching item (if any) and capacity data
     */
    public final ExtendedItemResult<T> getItemExtended(T value, boolean useConsistentRead) {
        return getItemExtended(getPartitionValue(value), getSortValue(value), useConsistentRead);
    }

    /**
     * Read an item from the table. Capacity data will be returned, and consistent read may be used
     * @param key A key holding the partition and sort keys
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A result that contains the matching item (if any) and capacity data
     */
    public final ExtendedItemResult<T> getItemExtended(Key<PartitionT, SortT> key, boolean useConsistentRead) {
        return getItemExtended(key.getPartition(), key.getSort(), useConsistentRead);
    }

    /**
     * Read an item from the table. Capacity data will be returned, and consistent read may be used
     * @param partitionValue The partition value to use
     * @param sortValue The sort value to use, or null if this table has no sort key
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A result that contains the matching item (if any) and capacity data
     */
    public ExtendedItemResult<T> getItemExtended(PartitionT partitionValue, SortT sortValue, boolean useConsistentRead) {
        var response = rawGetItem(GetItemRequest.builder()
                               .tableName(getTableName())
                               .key(keysToMap(partitionValue, sortValue))
                               .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                               .consistentRead(useConsistentRead)
                               .build());
        return new ExtendedItemResult<>(response.hasItem() ? decode(response.item()) : null,
            response.consumedCapacity());
    }

    /**
     * Process a request to get an item
     * @param request The request
     * @return The response from dynamo
     */
    private GetItemResponse rawGetItem(GetItemRequest request) {
        return getClient() == null ? getAsyncClient().getItem(request).join() : getClient().getItem(request);
    }

    /**
     * Read an item from the table asynchronously
     * @param value The partition and sort keys will be read from this item and used to fetch the result
     * @return A future that contains the item stored in the table with partition and sort keys that match value, or null if no such item
     *         exists
     */
    public final CompletableFuture<T> getItemAsync(T value) {
        return getItemAsync(getPartitionValue(value), getSortValue(value));
    }

    /**
     * Read an item from the table asynchronously
     * @param key A key holding the partition and sort keys
     * @return A future that contains the item stored in the table with partition and sort keys that match value, or null if no such item
     *         exists
     */
    public final CompletableFuture<T> getItemAsync(Key<PartitionT, SortT> key) {
        return getItemAsync(key.getPartition(), key.getSort());
    }

    /**
     * Read an item from the table asynchronously
     * @param partitionValue The partition value to use
     * @param sortValue The sort value to use, or null if this table has no sort key
     * @return A future that contains the item stored in the table with partition and sort keys that match value, or null if no such item
     *         exists
     */
    public CompletableFuture<T> getItemAsync(PartitionT partitionValue, SortT sortValue) {
        return rawGetItemAsync(GetItemRequest.builder()
                            .tableName(getTableName())
                            .key(keysToMap(partitionValue, sortValue))
                            .build())
                   .thenApply(r -> r.hasItem() ? decode(r.item()) : null);
    }

    /**
     * Read an item from the table asynchronously. Capacity data will be returned, and consistent read may be used
     * @param value The partition and sort keys will be read from this item and used to fetch the result
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A future that contains the matching item (if any) and capacity data
     */
    public final CompletableFuture<ExtendedItemResult<T>> getItemExtendedAsync(T value, boolean useConsistentRead) {
        return getItemExtendedAsync(getPartitionValue(value), getSortValue(value), useConsistentRead);
    }

    /**
     * Read an item from the table asynchronously. Capacity data will be returned, and consistent read may be used
     * @param key A key holding the partition and sort keys
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A future that contains the matching item (if any) and capacity data
     */
    public final CompletableFuture<ExtendedItemResult<T>> getItemExtendedAsync(Key<PartitionT, SortT> key, boolean useConsistentRead) {
        return getItemExtendedAsync(key.getPartition(), key.getSort(), useConsistentRead);
    }

    /**
     * Read an item from the table asynchronously. Capacity data will be returned, and consistent read may be used
     * @param partitionValue The partition value to use
     * @param sortValue The sort value to use, or null if this table has no sort key
     * @param useConsistentRead If true, then consistent reads will be used
     * @return A future that contains the matching item (if any) and capacity data
     */
    public CompletableFuture<ExtendedItemResult<T>> getItemExtendedAsync(PartitionT partitionValue, SortT sortValue, boolean useConsistentRead) {
        return rawGetItemAsync(GetItemRequest.builder()
                            .tableName(getTableName())
                            .key(keysToMap(partitionValue, sortValue))
                            .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                            .consistentRead(useConsistentRead)
                            .build())
                   .thenApply(r -> new ExtendedItemResult<>(r.hasItem() ? decode(r.item()) : null, r.consumedCapacity()));
    }

    /**
     * Process an asynchronous request to get an item
     * @param request The request
     * @return A future containing the response from dynamo
     */
    private CompletableFuture<GetItemResponse> rawGetItemAsync(GetItemRequest request) {
        return getAsyncClient() == null
               ? CompletableFuture.supplyAsync(() -> getClient().getItem(request))
               : getAsyncClient().getItem(request);
    }

    /**
     * Get a batch of items. This can't be named "getBatch" because erasure makes it the same as the by-key version.
     * @param items The items whose keys will be used to get the batch
     * @return The result of the read
     */
    public List<T> getBatchByItem(List<T> items) {
        return getBatch(buildGetBatchesFromItems(items));
    }

    /**
     * Get a batch of items. This can't be named "getBatch" because erasure makes it the same as the by-item version.
     * @param keys The keys used to get the batch
     * @return The result of the read
     */
    public List<T> getBatchByKey(List<Key<PartitionT, SortT>> keys) {
        return getBatch(buildGetBatchesFromKeys(keys));
    }

    /**
     * Given a list of batches of attribute/value keys (at most {@link #MAX_ITEMS_PER_BATCH} in each batch), submit all
     * matches and collect the results into a single list.
     * @param batches The list of batches to fetch
     * @return The results of all batches combined together
     */
    private List<T> getBatch(List<Map<String, KeysAndAttributes>> batches) {
        Function<BatchGetItemRequest, BatchGetItemResponse> doCall = (getClient() == null
                                                                      ? req -> getAsyncClient().batchGetItem(req).join()
                                                                      : req -> getClient().batchGetItem(req));
        return batches.stream()
                   .map(batch -> BatchGetItemRequest.builder().requestItems(batch).build())
                   .map(doCall)
                   .filter(BatchGetItemResponse::hasResponses)
                   .flatMap(response -> response.responses().get(getTableName()).stream())
                   .map(this::decode)
                   .collect(Collectors.toList());
    }

    /**
     * Get a batch of items with capacity data and the non-matching keys
     * @param items The items whose keys will be used to get the batch
     * @return The result of the read and the capacity data
     */
    public ExtendedBatchResult<T, Key<PartitionT, SortT>> getBatchByItemExtended(List<T> items) {
        return getBatchExtended(buildGetBatchesFromItems(items));
    }

    /**
     * Get a batch of items with capacity data and the non-matching keys
     * @param keys The keys used to get the batch
     * @return The result of the read and the capacity data
     */
    public ExtendedBatchResult<T, Key<PartitionT, SortT>> getBatchByKeyExtended(List<Key<PartitionT, SortT>> keys) {
        return getBatchExtended(buildGetBatchesFromKeys(keys));
    }

    /**
     * Given a list of batches of attribute/value keys (at most {@link #MAX_ITEMS_PER_BATCH} in each batch), submit all
     * matches and collect the results into a single list.
     * @param batches The list of batches to fetch
     * @return The results of the read
     */
    private ExtendedBatchResult<T, Key<PartitionT, SortT>> getBatchExtended(List<Map<String, KeysAndAttributes>> batches) {
        var result = new ExtendedBatchResult<T, Key<PartitionT, SortT>>();
        for (var batch : batches) {
            var request = BatchGetItemRequest.builder()
                              .requestItems(batch)
                              .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                              .build();
            updateExtendedGetBatchResult(result,
                getClient() == null ? getAsyncClient().batchGetItem(request).join() : getClient().batchGetItem(request));
        }
        return result;
    }

    /**
     * Get a batch of items asynchronously.
     * @param items The items whose keys will be used to get the batch
     * @return A future containing the result of the read
     */
    public CompletableFuture<List<T>> getBatchByItemAsync(List<T> items) {
        return getBatchAsync(buildGetBatchesFromItems(items));
    }

    /**
     * Get a batch of items asynchronously.
     * @param keys The keys used to get the batch
     * @return A future containing the results of all batches combined together
     */
    public CompletableFuture<List<T>> getBatchByKeyAsync(List<Key<PartitionT, SortT>> keys) {
        return getBatchAsync(buildGetBatchesFromKeys(keys));
    }

    /**
     * Given a list of batches of attribute/value keys (at most {@link #MAX_ITEMS_PER_BATCH} in each batch), asynchronously submit all
     * matches and collect the results into a single list.
     * @param batches The list of batches to fetch
     * @return The results of all batches combined together
     */
    private CompletableFuture<List<T>> getBatchAsync(List<Map<String, KeysAndAttributes>> batches) {
        BiFunction<List<T>, BatchGetItemResponse, List<T>> merger = (list, response) -> {
            if (response.hasResponses()) {
                response.responses().get(getTableName()).forEach(m -> list.add(decode(m)));
            }
            return list;
        };
        CompletableFuture<List<T>> result = CompletableFuture.completedFuture(new ArrayList<>(batches.size() * MAX_ITEMS_PER_BATCH));
        for (var batch: batches) {
            var request = BatchGetItemRequest.builder().requestItems(batch).build();
            result = result.thenCombine(
                getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().batchGetItem(request))
                                         : getAsyncClient().batchGetItem(request),
                merger);
        }
        return result;
    }

    /**
     * Asynchronously get a batch of items with capacity data and the non-matching keys
     * @param items The items whose keys will be used to get the batch
     * @return A future with the result of the read and the capacity data
     */
    public CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> getBatchByItemExtendedAsync(List<T> items) {
        return getBatchExtendedAsync(buildGetBatchesFromItems(items));
    }

    /**
     * Asynchronously get a batch of items with capacity data and the non-matching keys
     * @param keys The keys used to get the batch
     * @return A future with the result of the read and the capacity data
     */
    public CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> getBatchByKeyExtendedAsync(List<Key<PartitionT, SortT>> keys) {
        return getBatchExtendedAsync(buildGetBatchesFromKeys(keys));
    }

    /**
     * Given a list of batches of attribute/value keys (at most {@link #MAX_ITEMS_PER_BATCH} in each batch), submit all
     * matches and collect the results into a single list.
     * @param batches The list of batches to fetch
     * @return A future with the results of the read
     */
    private CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> getBatchExtendedAsync(List<Map<String, KeysAndAttributes>> batches) {
        var result = CompletableFuture.completedFuture(new ExtendedBatchResult<T, Key<PartitionT, SortT>>());
        for (var batch : batches) {
            var request = BatchGetItemRequest.builder()
                              .requestItems(batch)
                              .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                              .build();
            result = result.thenCombine(
                getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().batchGetItem(request))
                                         : getAsyncClient().batchGetItem(request),
                this::updateExtendedGetBatchResult);
        }
        return result;
    }

    /**
     * Update an extended get batch result with a response from Dynamo
     * @param result The result that we will return to the user
     * @param response The response from dynamo
     * @return The result (after updating it)
     */
    private ExtendedBatchResult<T, Key<PartitionT, SortT>> updateExtendedGetBatchResult(ExtendedBatchResult<T, Key<PartitionT, SortT>> result, BatchGetItemResponse response) {
        if (response.hasResponses()) {
            response.responses().get(getTableName()).stream()
                .map(this::decode)
                .forEach(item -> result.getItems().add(item));
        }
        if (response.hasConsumedCapacity()) {
            for (var cap : response.consumedCapacity()) {
                result.getCapacity().add(cap);
            }
        }
        if (response.hasUnprocessedKeys()) {
            for (var keyMap : response.unprocessedKeys().get(getTableName()).keys()) {
                result.getUnprocessedValues().add(new Key<>(getPartitionValue(keyMap.get(getPartitionKeyAttribute())),
                    getSortValue(keyMap.get(getSortKeyAttribute()))));
            }
        }
        return result;
    }

    /**
     * Build a list of maps from table name to a list of keys. Each map will have at most MAX_ITEMS_PER_BATCH items.
     * @param items A list of items to use as sort keys
     * @return A list of batches to submit for processing
     */
    private List<Map<String, KeysAndAttributes>> buildGetBatchesFromItems(List<T> items) {
        int numItems = items.size();
        var result = new ArrayList<Map<String, KeysAndAttributes>>((numItems + MAX_ITEMS_PER_BATCH - 1) / MAX_ITEMS_PER_BATCH);
        for (int offset = 0; offset < numItems; offset += MAX_ITEMS_PER_BATCH) {
            result.add(Collections.singletonMap(getTableName(),
                KeysAndAttributes.builder()
                    .keys(items.subList(offset, Math.min(numItems, offset + MAX_ITEMS_PER_BATCH)).stream()
                              .map(item -> keysToMap(getPartitionValue(item), getSortValue(item)))
                              .collect(Collectors.toList()))
                    .build()));
        }
        return result;
    }

    /**
     * Build a list of maps from table name to a list of keys. Each map will have at most MAX_ITEMS_PER_BATCH items.
     * @param keys A list of keys
     * @return A list of batches to submit for processing
     */
    private List<Map<String, KeysAndAttributes>> buildGetBatchesFromKeys(List<Key<PartitionT, SortT>> keys) {
        int numItems = keys.size();
        var result = new ArrayList<Map<String, KeysAndAttributes>>((numItems + MAX_ITEMS_PER_BATCH - 1) / MAX_ITEMS_PER_BATCH);
        for (int offset = 0; offset < numItems; offset += MAX_ITEMS_PER_BATCH) {
            result.add(Collections.singletonMap(getTableName(),
                KeysAndAttributes.builder()
                    .keys(keys.subList(offset, Math.min(numItems, offset + MAX_ITEMS_PER_BATCH)).stream()
                              .map(key -> keysToMap(key.getPartition(), key.getSort()))
                              .collect(Collectors.toList()))
                    .build()));
        }
        return result;
    }

    /**
     * Write an item to the database
     * @param value The item to write
     */
    public void putItem(T value) {
        rawPutItem(PutItemRequest.builder()
                       .tableName(getTableName())
                       .item(encode(value))
                       .build());
    }

    /**
     * Write multiple items to the database
     * @param values The items to write
     */
    public void putBatch(List<T> values) {
        int numValues = values.size();
        for (int i = 0; i < numValues; i += MAX_ITEMS_PER_BATCH) {
            var request = BatchWriteItemRequest.builder()
                              .requestItems(Collections.singletonMap(getTableName(),
                                  values.subList(i, Math.min(numValues, i + MAX_ITEMS_PER_BATCH)).stream()
                                      .map(value -> WriteRequest.builder().putRequest(PutRequest.builder()
                                                                                          .item(encode(value)).build()).build())
                                      .collect(Collectors.toList())))
                              .build();
            if (getClient() == null) {
                getAsyncClient().batchWriteItem(request).join();
            } else {
                getClient().batchWriteItem(request);
            }
        }
    }

    /**
     * Write an item to the database, returning the previous value (if any) and the consumed capacity
     * @param value The value to write
     * @param expected An optional condition expression
     * @param returnPrevious If true, then the previous value at these keys (if any) will be returned
     * @return A result that contains the consumed capacity and the replaced item
     * @throws software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException If the conditional
     *         expression is provided and evaluates as false
     */
    public ExtendedItemResult<T> putItemExtended(T value, ConditionExpression expected, boolean returnPrevious) {
        var putBuilder = PutItemRequest.builder()
                             .tableName(getTableName())
                             .item(encode(value))
                             .returnValues(returnPrevious ? ReturnValue.ALL_OLD : ReturnValue.NONE)
                             .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES);
        if (expected != null) {
            putBuilder.conditionExpression(expected.getExpression())
                .expressionAttributeNames(expected.getAttributeNames())
                .expressionAttributeValues(expected.getValues());
        }
        var putResponse = rawPutItem(putBuilder.build());
        return new ExtendedItemResult<>(putResponse.hasAttributes() ? decode(putResponse.attributes()) : null,
            putResponse.consumedCapacity());
    }

    /**
     * Put a batch of items, returning the consumed capacity and the unprocessed values
     * @param values Items to put to the database
     * @return An extended batch result containing the consumed capacity and the unprocessed values
     */
    public ExtendedBatchResult<T, T> putBatchExtended(List<T> values) {
        int numValues = values.size();
        var result = new ExtendedBatchResult<T, T>();
        for (int i = 0; i < numValues; i += MAX_ITEMS_PER_BATCH) {
            var request = BatchWriteItemRequest.builder()
                              .requestItems(Collections.singletonMap(getTableName(),
                                  values.subList(i, Math.min(numValues, i + MAX_ITEMS_PER_BATCH)).stream()
                                      .map(value -> WriteRequest.builder().putRequest(PutRequest.builder()
                                                                                          .item(encode(value)).build()).build())
                                      .collect(Collectors.toList())))
                              .build();
            var response = getClient() == null ? getAsyncClient().batchWriteItem(request).join() : getClient().batchWriteItem(request);
            updateBatchResultForPut(result, response);
        }
        return result;
    }

    /**
     * Add an item to the database asynchronously
     * @param value The item to add
     * @return A future that indicates when the operation is complete
     */
    public CompletableFuture<Void> putItemAsync(T value) {
        return rawPutItemAsync(PutItemRequest.builder()
                                .tableName(getTableName())
                                .item(encode(value))
                                .build())
                   .thenAccept(response -> { });
    }

    /**
     * Add a batch of items to the database asynchronously
     * @param values The items to add
     * @return A future that indicates when the operation is complete
     */
    public CompletableFuture<Void> putBatchAsync(List<T> values) {
        int numValues = values.size();
        var futures = new CompletableFuture<?>[(numValues + MAX_ITEMS_PER_BATCH) / MAX_ITEMS_PER_BATCH];
        for (int i = 0; i < numValues; i += MAX_ITEMS_PER_BATCH) {
            var request = BatchWriteItemRequest.builder()
                              .requestItems(Collections.singletonMap(getTableName(),
                                  values.subList(i, Math.min(numValues, i + MAX_ITEMS_PER_BATCH)).stream()
                                      .map(value -> WriteRequest.builder().putRequest(PutRequest.builder()
                                                                                          .item(encode(value)).build()).build())
                                      .collect(Collectors.toList())))
                              .build();
            futures[i / MAX_ITEMS_PER_BATCH] =
                getAsyncClient() == null ? CompletableFuture.runAsync(() -> getClient().batchWriteItem(request))
                                         : getAsyncClient().batchWriteItem(request);
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * Write an item to the database asynchronously, returning the previous value (if any) and the consumed capacity
     * @param value The value to write
     * @param expected An optional condition expression
     * @param returnPrevious If set, return the previous value at this key
     * @return A future that contains the consumed capacity and the replaced item and throws {@link software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException}
     *         if the conditional expression is provided and evaluates as false
     */
    public CompletableFuture<ExtendedItemResult<T>> putItemExtendedAsync(T value, ConditionExpression expected, boolean returnPrevious) {
        var putBuilder = PutItemRequest.builder()
                             .tableName(getTableName())
                             .item(encode(value))
                             .returnValues(returnPrevious ? ReturnValue.ALL_OLD : ReturnValue.NONE)
                             .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES);
        if (expected != null) {
            putBuilder.conditionExpression(expected.getExpression())
                .expressionAttributeNames(expected.getAttributeNames())
                .expressionAttributeValues(expected.getValues());
        }
        return rawPutItemAsync(putBuilder.build())
                   .thenApply(resp -> new ExtendedItemResult<>(
                       resp.hasAttributes() ? decode(resp.attributes()) : null, resp.consumedCapacity()));
    }

    /**
     * Add a batch of items to the database asynchronously
     * @param values The items to add
     * @return A future that contains the capacity consumption values and the unprocesed items
     */
    public CompletableFuture<ExtendedBatchResult<T, T>> putBatchExtendedAsync(List<T> values) {
        int numValues = values.size();
        var result = CompletableFuture.completedFuture(new ExtendedBatchResult<T, T>());
        for (int i = 0; i < numValues; i += MAX_ITEMS_PER_BATCH) {
            var request = BatchWriteItemRequest.builder()
                              .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                              .requestItems(Collections.singletonMap(getTableName(),
                                  values.subList(i, Math.min(numValues, i + MAX_ITEMS_PER_BATCH)).stream()
                                      .map(value -> WriteRequest.builder().putRequest(PutRequest.builder()
                                                                                          .item(encode(value)).build()).build())
                                      .collect(Collectors.toList())))
                              .build();
            result = result.thenCombine(
                getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().batchWriteItem(request))
                                         : getAsyncClient().batchWriteItem(request),
                this::updateBatchResultForPut);
        }
        return result;
    }

    /**
     * Perform a put item on the dynamo client
     * @param request The request
     * @return The response
     */
    private PutItemResponse rawPutItem(PutItemRequest request) {
        return getClient() == null ? getAsyncClient().putItem(request).join() : getClient().putItem(request);
    }

    /**
     * Perform an asynchronous put item on the dynamo client
     * @param request The requste
     * @return A future containing the response
     */
    private CompletableFuture<PutItemResponse> rawPutItemAsync(PutItemRequest request) {
        return getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().putItem(request)) : getAsyncClient().putItem(request);
    }

    /**
     * Update a batch result with the data from a batch write response
     * @param result The result
     * @param response The response
     * @return The result
     */
    private ExtendedBatchResult<T, T> updateBatchResultForPut(ExtendedBatchResult<T, T> result, BatchWriteItemResponse response) {
        if (response.hasUnprocessedItems() && !response.unprocessedItems().isEmpty()) {
            response.unprocessedItems().get(getTableName()).stream()
                .map(writeRequest -> decode(writeRequest.putRequest().item()))
                .forEach(item -> result.getUnprocessedValues().add(item));
        }
        if (response.hasConsumedCapacity()) {
            response.consumedCapacity().forEach(cap -> result.getCapacity().add(cap));
        }
        return result;
    }

    /**
     * Delete an item from the database. Items do not need to equal the value provided, any item with the same partition
     * and sort keys as the provided value will be deleted
     * @param value A value whose partition and sort keys indicate what to remove
     */
    public final void deleteItem(T value) {
        deleteItem(getPartitionValue(value), getSortValue(value));
    }

    /**
     * Delete an item from the database
     * @param key The partition and sort keys of the item to delete
     */
    public final void deleteItem(Key<PartitionT, SortT> key) {
        deleteItem(key.getPartition(), key.getSort());
    }

    /**
     * Delete an item from the database
     * @param partitionKey The partition key of the item to be deleted
     * @param sortKey The sort key of the item to be deleted
     */
    public void deleteItem(PartitionT partitionKey, SortT sortKey) {
        var deleteResponse = rawDeleteItem(DeleteItemRequest.builder()
                                            .tableName(getTableName())
                                            .key(keysToMap(partitionKey, sortKey))
                                            .build());
    }

    /**
     * Delete a batch of items. This can't be named "deleteBatch" because erasure makes it the same as the by-key version.
     * @param items Items whose partition and sort keys match the items to be deleted
     */
    public void deleteBatchByItem(List<T> items) {
        deleteBatch(buildDeleteBatchesFromItems(items));
    }

    /**
     * Delete a batch of items. This can't be named "deleteBatch" because erasure makes it the same as the by-key version.
     * @param keys The keys of the items to be deleted
     */
    public void deleteBatchByKey(List<Key<PartitionT, SortT>> keys) {
        deleteBatch(buildDeleteBatchesFromKeys(keys));
    }

    /**
     * Delete several batches of items
     * @param batches The batches of items to delete
     */
    private void deleteBatch(List<Map<String, List<WriteRequest>>> batches) {
        for (var batch: batches) {
            var req = BatchWriteItemRequest.builder().requestItems(batch).build();
            if (getClient() == null) {
                getAsyncClient().batchWriteItem(req).join();
            } else {
                getClient().batchWriteItem(req);
            }
        }
    }

    /**
     * Delete an item, returning the item deleted and the capacity consumed
     * @param value An item with the same partition and sort keys as the item to delete
     * @param returnPrevious If true, then the deleted item is returned
     * @return An extended item result with consumption and the deleted item (if any) filled in
     */
    public final ExtendedItemResult<T> deleteItemExtended(T value, boolean returnPrevious) {
        return deleteItemExtended(getPartitionValue(value), getSortValue(value), returnPrevious);
    }

    /**
     * Delete an item, returning the item deleted and the capacity consumed
     * @param value The partition and sort keys of the item to delete
     * @param returnPrevious If true, then the deleted item is returned
     * @return An extended item result with consumtion and the deleted item (if any) filled in
     */
    public final ExtendedItemResult<T> deleteItemExtended(Key<PartitionT, SortT> value, boolean returnPrevious) {
        return deleteItemExtended(value.getPartition(), value.getSort(), returnPrevious);
    }

    /**
     * Delete an item, returning the item deleted and the capacity consumed
     * @param partitionKey The partition key of the item to delete
     * @param sortKey The sort key of the item to delete
     * @param returnPrevious If true, then the deleted item is returned
     * @return An extended item result with consumtion and the deleted item (if any) filled in
     */
    public ExtendedItemResult<T> deleteItemExtended(PartitionT partitionKey, SortT sortKey, boolean returnPrevious) {
        var deleteResponse = rawDeleteItem(DeleteItemRequest.builder()
                                            .tableName(getTableName())
                                            .key(keysToMap(partitionKey, sortKey))
                                            .returnValues(returnPrevious ? ReturnValue.ALL_OLD : ReturnValue.NONE)
                                            .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                                            .build());
        return new ExtendedItemResult<>(deleteResponse.hasAttributes() ? decode(deleteResponse.attributes())
                                                                       : null, deleteResponse.consumedCapacity());
    }

    /**
     * Delete a batch of items, returning the capacity consumed and the unprocessed values
     * @param items The items whose keys will be used to get the batch
     * @return An extended batch result with the unprocessed values and the capacity consumed
     */
    public ExtendedBatchResult<T, Key<PartitionT, SortT>> deleteBatchByItemExtended(List<T> items) {
        return deleteBatchExtended(buildDeleteBatchesFromItems(items));
    }

    /**
     * Delete a batch of items, returning the capacity consumed and the unprocessed values
     * @param keys The partition and sort keys to delete
     * @return An extended batch result with the unprocessed values and the capacity consumed
     */
    public ExtendedBatchResult<T, Key<PartitionT, SortT>> deleteBatchByKeyExtended(List<Key<PartitionT, SortT>> keys) {
        return deleteBatchExtended(buildDeleteBatchesFromKeys(keys));
    }

    /**
     * Delete a series of batches, collecting the results into an extended batch result
     * @param batches The batches of items ot delete
     * @return An extended batch result with the unprocessed values and the capacity consumed
     */
    private ExtendedBatchResult<T, Key<PartitionT, SortT>> deleteBatchExtended(List<Map<String, List<WriteRequest>>> batches) {
        var result = new ExtendedBatchResult<T, Key<PartitionT, SortT>>();
        for (var batch: batches) {
            var req = BatchWriteItemRequest.builder().requestItems(batch).build();
            updateBatchResultForDelete(result, getClient() == null ? getAsyncClient().batchWriteItem(req).join() : getClient().batchWriteItem(req));
        }
        return result;
    }

    /**
     * Delete an item from the database asynchronously. Items do not need to equal the value provided, any item with the same partition
     * and sort keys as the provided value will be deleted
     * @param value A value whose partition and sort keys indicate what to remove
     * @return A future that indicates when the operation is complete
     */
    public final CompletableFuture<Void> deleteItemAsync(T value) {
        return deleteItemAsync(getPartitionValue(value), getSortValue(value));
    }

    /**
     * Delete an item from the database asynchronously
     * @param key The partition and sort keys of the item to delete
     * @return A future that indicates when the operation is complete
     */
    public final CompletableFuture<Void> deleteItemAsync(Key<PartitionT, SortT> key) {
        return deleteItemAsync(key.getPartition(), key.getSort());
    }

    /**
     * Delete an item from the database asynchronously
     * @param partitionKey The partition key of the item to be deleted
     * @param sortKey The sort key of the item to be deleted
     * @return A future that indicates when the operation is complete
     */
    public CompletableFuture<Void> deleteItemAsync(PartitionT partitionKey, SortT sortKey) {
        return rawDeleteItemAsync(DeleteItemRequest.builder()
                               .tableName(getTableName())
                               .key(keysToMap(partitionKey, sortKey))
                               .build())
                   .thenAccept(resp -> { });
    }

    /**
     * Delete a batch of items asynchronously
     * @param items The items whose keys will be used to get the batch
     * @return A future that indicates when the operation is complete
     */
    public CompletableFuture<Void> deleteBatchByItemAsync(List<T> items) {
        return deleteBatchAsync(buildDeleteBatchesFromItems(items));
    }

    /**
     * Delete a batch of items asynchronously
     * @param keys The keys used to get the batch
     * @return A future that indicates when the operation is complete
     */
    public CompletableFuture<Void> deleteBatchByKeyAsync(List<Key<PartitionT, SortT>> keys) {
        return deleteBatchAsync(buildDeleteBatchesFromKeys(keys));
    }

    /**
     * Delete a list of batches from the database
     * @param batches The batches to delete
     * @return A future that indicates when the operation is complete
     */
    private CompletableFuture<Void> deleteBatchAsync(List<Map<String, List<WriteRequest>>> batches) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[batches.size()];
        for (var i = 0; i < batches.size(); ++i) {
            var req = BatchWriteItemRequest.builder().requestItems(batches.get(i)).build();
            futures[i] = getAsyncClient() == null ? CompletableFuture.runAsync(() -> getClient().batchWriteItem(req)) : getAsyncClient().batchWriteItem(req);
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * Delete an item asynchronously, returning the item deleted and the capacity consumed
     * @param value An item with the same partition and sort keys as the item to delete
     * @param returnPrevious If true, then the deleted value will be returned
     * @return A future containing consumption and the deleted item (if any) filled in
     */
    public final CompletableFuture<ExtendedItemResult<T>> deleteItemExtendedAsync(T value, boolean returnPrevious) {
        return deleteItemExtendedAsync(getPartitionValue(value), getSortValue(value), returnPrevious);
    }

    /**
     * Delete an item asynchronously, returning the item deleted and the capacity consumed
     * @param key The partition and sort keys of the item to delete
     * @param returnPrevious If true, then the deleted value will be returned
     * @return A future containing consumption and the deleted item (if any) filled in
     */
    public final CompletableFuture<ExtendedItemResult<T>> deleteItemExtendedAsync(Key<PartitionT, SortT> key, boolean returnPrevious) {
        return deleteItemExtendedAsync(key.getPartition(), key.getSort(), returnPrevious);
    }

    /**
     * Delete an item, returning the item deleted and the capacity consumed
     * @param partitionKey The partition key of the item to delete
     * @param sortKey The sort key of the item to delete
     * @param returnPrevious If true, then the deleted value will be returned
     * @return An extended item result with consumtion and the deleted item (if any) filled in
     */
    public CompletableFuture<ExtendedItemResult<T>> deleteItemExtendedAsync(PartitionT partitionKey, SortT sortKey, boolean returnPrevious) {
        return rawDeleteItemAsync(DeleteItemRequest.builder()
                                   .tableName(getTableName())
                                   .key(keysToMap(partitionKey, sortKey))
                                   .returnConsumedCapacity(ReturnConsumedCapacity.INDEXES)
                                   .returnValues(returnPrevious ? ReturnValue.ALL_OLD : ReturnValue.NONE)
                                   .build())
                   .thenApply(resp -> new ExtendedItemResult<>(
                       resp.hasAttributes() ? decode(resp.attributes()) : null, resp.consumedCapacity()));
    }

    /**
     * Get a batch of items. This can't be named "getBatch" because erasure makes it the same as the by-key version.
     * @param items The items whose keys will be used to get the batch
     * @return A future containing the consumed capacity and the unprocessed values
     */
    public CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> deleteBatchByItemExtendedAsync(List<T> items) {
        return deleteBatchExtendedAsync(buildDeleteBatchesFromItems(items));
    }

    /**
     * Get a batch of items. This can't be named "getBatch" because erasure makes it the same as the by-item version.
     * @param keys The keys used to get the batch
     * @return A future containing the consumed capacity and the unprocessed values
     */
    public CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> deleteBatchByKeyExtendedAsync(List<Key<PartitionT, SortT>> keys) {
        return deleteBatchExtendedAsync(buildDeleteBatchesFromKeys(keys));
    }

    /**
     * Asynchronously delete batches of items
     * @param batches A list of the batches of items to delete
     * @return A future containing the unprocessed items and the consumed capacity
     */
    private CompletableFuture<ExtendedBatchResult<T, Key<PartitionT, SortT>>> deleteBatchExtendedAsync(List<Map<String, List<WriteRequest>>> batches) {
        var futureResult = CompletableFuture.completedFuture(new ExtendedBatchResult<T, Key<PartitionT, SortT>>());
        for (var batch : batches) {
            var req = BatchWriteItemRequest.builder().requestItems(batch).build();
            futureResult = futureResult.thenCombine(
                getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().batchWriteItem(req))
                                         : getAsyncClient().batchWriteItem(req),
                this::updateBatchResultForDelete);
        }
        return futureResult;
    }

    /**
     * Combine a dynamo batch delete result with an existing ExtendedBatchResult
     * @param result The result
     * @param response The response from Dynamo
     * @return The result (after merging the data from Dynamo)
     */
    private ExtendedBatchResult<T, Key<PartitionT, SortT>> updateBatchResultForDelete(ExtendedBatchResult<T, Key<PartitionT, SortT>> result, BatchWriteItemResponse response) {
        if (response.hasUnprocessedItems() && !response.unprocessedItems().isEmpty()) {
            response.unprocessedItems().get(getTableName()).stream()
                .map(writeRequest -> writeRequest.deleteRequest().key())
                .map(m -> new Key<>(getPartitionValue(m.get(getPartitionKeyAttribute())), getSortValue(m.get(getSortKeyAttribute()))))
                .forEach(k -> result.getUnprocessedValues().add(k));
        }
        if (response.hasConsumedCapacity()) {
            response.consumedCapacity().forEach(cap -> result.getCapacity().add(cap));
        }
        return result;
    }

    /**
     * Submit a delete request to dynamo
     * @param request The request
     * @return The response
     */
    private DeleteItemResponse rawDeleteItem(DeleteItemRequest request) {
        return getClient() == null ? getAsyncClient().deleteItem(request).join() : getClient().deleteItem(request);
    }

    /**
     * Submit a delete request to dynamo asynchronously
     * @param request The request
     * @return The response
     */
    private CompletableFuture<DeleteItemResponse> rawDeleteItemAsync(DeleteItemRequest request) {
        return getAsyncClient() == null ? CompletableFuture.supplyAsync(() -> getClient().deleteItem(request)) : getAsyncClient().deleteItem(request);
    }

    /**
     * Get an index. Usually you don't want to call this; for each index in your table, a function "getXxxIndex" function
     * will be added that will return the index more easily.
     * @param name The name of this index
     * @param secondaryPartitionClass The class of the index partition key or null
     * @param secondarySortClass The class of the index sort key or null
     * @param <SecondaryPartitionT> The type of the index partition key
     * @param <SecondarySortT> The type of the index sort key
     * @return The index
     * @throws IllegalArgumentException If the partition classes are provided but do not match the index, or if there
     *         is no index by the provided name
     */
    public abstract <SecondaryPartitionT, SecondarySortT> DynamoIndex<T, SecondaryPartitionT, SecondarySortT> getIndex(
        String name, Class<SecondaryPartitionT> secondaryPartitionClass, Class<SecondarySortT> secondarySortClass);

    /**
     * Build a list of maps from table name to a list of keys. Each map will have at most MAX_ITEMS_PER_BATCH items.
     * @param items A list of items to use as sort keys
     * @return A list of batches to submit for processing
     */
    private List<Map<String, List<WriteRequest>>> buildDeleteBatchesFromItems(List<T> items) {
        int numItems = items.size();
        var result = new ArrayList<Map<String, List<WriteRequest>>>((numItems + MAX_ITEMS_PER_BATCH - 1) / MAX_ITEMS_PER_BATCH);
        for (int offset = 0; offset < numItems; offset += MAX_ITEMS_PER_BATCH) {
            result.add(Collections.singletonMap(getTableName(),
                items.subList(offset, Math.min(numItems, offset + MAX_ITEMS_PER_BATCH)).stream()
                    .map(item -> WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(keysToMap(getPartitionValue(item), getSortValue(item))).build()).build())
                    .collect(Collectors.toList())));
        }
        return result;
    }

    /**
     * Build a list of maps from table name to a list of keys. Each map will have at most MAX_ITEMS_PER_BATCH items.
     * @param keys A list of keys
     * @return A list of batches to submit for processing
     */
    private List<Map<String, List<WriteRequest>>> buildDeleteBatchesFromKeys(List<Key<PartitionT, SortT>> keys) {
        int numItems = keys.size();
        var result = new ArrayList<Map<String, List<WriteRequest>>>((numItems + MAX_ITEMS_PER_BATCH - 1) / MAX_ITEMS_PER_BATCH);
        for (int offset = 0; offset < numItems; offset += MAX_ITEMS_PER_BATCH) {
            result.add(Collections.singletonMap(getTableName(),
                keys.subList(offset, Math.min(numItems, offset + MAX_ITEMS_PER_BATCH)).stream()
                    .map(key -> WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(keysToMap(key.getPartition(), key.getSort())).build()).build())
                    .collect(Collectors.toList())));
        }
        return result;
    }

    /**
     * Extract the partition value from an attribute value
     * @param value The attribute value
     * @return The partition value
     */
    protected abstract PartitionT getPartitionValue(AttributeValue value);

    /**
     * Extract a sort value from an attribute value
     * @param value The attribute value
     * @return The sort value
     */
    protected abstract SortT getSortValue(AttributeValue value);

    /**
     * Convert the partition and sort keys to a map.
     * @param partitionValue The value of the partition key
     * @param sortValue The value of the sort key. Must be null if there is no sort key
     * @return A dynamo-friendly map of attribute values
     */
    protected final Map<String, AttributeValue> keysToMap(PartitionT partitionValue, SortT sortValue) {
        return sortValue == null
               ? Collections.singletonMap(getPartitionKeyAttribute(), partitionValueToAttributeValue(partitionValue))
               : Map.of(getPartitionKeyAttribute(), partitionValueToAttributeValue(partitionValue),
                   getSortKeyAttribute(), sortValueToAttributeValue(sortValue));
    }
}
