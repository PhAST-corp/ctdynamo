package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A client that uses DynamoIndex and DynamoTable objects to emulate an in-memory Dynamo database
 */
class MockDynamoClient implements DynamoDbClient {

    /** Recognizes the key expressions that ctDynamo generates */
    private static final Pattern KEY_EXPRESSION_OP = Pattern.compile(".*?([<>=]+) *:ctdynamo_s1.*");

    /**
     * Tables we know about. Weak references so they go away when no longer needed, although the entries stay in the
     * table. You must synchronize on this when you access it.
     *
     * <p>If we built mocks using a shared Dynamo client, then we could store the table-to-client mapping there,
     * which would be cleaner. But we have lots of unit tests that make mock tables willy-nilly and want to support
     * that old code, so having a static unit-test-only mapping from table name to mock client seems the only real
     * option.
     */
    private static final Map<String, WeakReference<MockDynamoClient>> TABLE_TO_MOCK_CLIENT = new HashMap<>();

    /**
     * We need this cancellation reason for each and every successful transaction statement, so rather than build
     * the same thing over and over we build it once, here, then re-use it.
     */
    private static final CancellationReason SUCCESS_REASON = CancellationReason.builder()
        .code("None")
        .build();

    /** The name of this index, or null if this is a table */
    private final String indexName;

    /** Our table */
    private final DynamoTable<?, ?, ?> tableInstance;

    /** Our data is held here */
    private final InMemoryDynamoStore store;

    /**
     * In a table, each index is a child MockClient. Requests are passed off to the child when the indexName property is
     * set. Index mock clients have no children
     */
    private final Map<String, MockDynamoClient> children = new HashMap<>();

    /**
     * Build a mock client for a table
     * @param tableInstance A table of the class that we will back
     * @param items Optional collection of items to add to the table
     */
    MockDynamoClient(DynamoTable<?, ?, ?> tableInstance, Collection<Map<String, AttributeValue>> items) {
        this.tableInstance = tableInstance;
        indexName = null;
        store = new InMemoryDynamoStore(tableInstance, null, items);
        if (!tableInstance.getTableName().equals("mock")) {
            synchronized(TABLE_TO_MOCK_CLIENT) {
                TABLE_TO_MOCK_CLIENT.put(tableInstance.getTableName(), new WeakReference<>(this));
            }
        }
    }

    /**
     * Build a mock client for an index
     * @param tableInstance A table of the class that we will back
     * @param indexInstance The index
     * @param items Optional collection of items to add to the table
     */
    MockDynamoClient(DynamoTable<?, ?, ?> tableInstance, DynamoIndex<?, ?, ?> indexInstance, Collection<Map<String, AttributeValue>> items) {
        this.tableInstance = tableInstance;
        indexName = indexInstance.getIndexName();
        store = new InMemoryDynamoStore(indexInstance, tableInstance.getSortKeyAttribute() == null
                                                       ? List.of(tableInstance.getPartitionKeyAttribute())
                                                       : List.of(tableInstance.getPartitionKeyAttribute(), tableInstance.getSortKeyAttribute()), items);
    }

    /**
     * Get the dynamo store backing this instance
     * @return The store backing this instance
     */
    InMemoryDynamoStore getStore() {
        return store;
    }

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public synchronized GetItemResponse getItem(GetItemRequest getItemRequest) {
        return GetItemResponse.builder()
                   .item(store.getItem(getItemRequest.key()))
                   .build();
    }

    @Override
    public synchronized PutItemResponse putItem(PutItemRequest putItemRequest) {
        var exprString = putItemRequest.conditionExpression();
        if (exprString != null) {
            var expr = new ConditionExpression(exprString, putItemRequest.expressionAttributeValues(), putItemRequest.expressionAttributeNames());
            var prevValue = Optional.ofNullable(store.getItem(putItemRequest.item()))
                                .orElse(Collections.emptyMap());
            if (!ExpressionEvaluator.evalBool(expr, prevValue)) {
                throw ConditionalCheckFailedException.builder().build();
            }
        }
        children.values().forEach(child -> child.store.add(putItemRequest.item()));
        return PutItemResponse.builder()
                   .attributes(store.add(putItemRequest.item()))
                   .build();
    }

    @Override
    public synchronized QueryResponse query(QueryRequest request) {
        if (!Objects.equals(indexName, request.indexName())) {
            return children.computeIfAbsent(request.indexName(), this::buildIndex).query(request);
        }
        var partitionValue = request.expressionAttributeValues().get(":ctdynamo_p");
        var s1 = request.expressionAttributeValues().get(":ctdynamo_s1");
        var s2 = request.expressionAttributeValues().get(":ctdynamo_s2");
        Collection<Map<String, AttributeValue>> rawItems;
        if (s1 == null) {
            rawItems = store.getRange(partitionValue, (AttributeValue)null, true, null, true);
        } else if ((s2 == null) && request.keyConditionExpression().indexOf("AND begins_with") > 0) {
            rawItems = getByPrefix(partitionValue, s1);
        } else if (s2 == null) {
            rawItems = getByComparator(partitionValue, s1, request.keyConditionExpression());
        } else {
            rawItems = getByBetween(partitionValue, s1, s2);
        }
        List<Map<String, AttributeValue>> items = new ArrayList<>(rawItems);
        if (request.scanIndexForward() == Boolean.FALSE) {
            Collections.reverse(items);
            if (request.hasExclusiveStartKey()) {
                items.removeIf(item -> store.getSortComparator().compare(request.exclusiveStartKey(), item) <= 0);
            }
        } else {
            if (request.hasExclusiveStartKey()) {
                items.removeIf(item -> store.getSortComparator().compare(request.exclusiveStartKey(), item) >= 0);
            }
        }
        if ((request.limit() != null) && (items.size() > request.limit())) {
            items = items.subList(0, request.limit());
        }
        if (request.filterExpression() != null) {
            var expr = new ConditionExpression(request.filterExpression(), request.expressionAttributeValues(), request.expressionAttributeNames());
            items.removeIf(item -> !ExpressionEvaluator.evalBool(expr, item));
        }
        return QueryResponse.builder()
                   .items(items)
                   .lastEvaluatedKey(items.isEmpty() ? null : items.get(items.size() - 1))
                   .build();
    }

    /**
     * Return a collection with all values whose sort key have the given prefix
     * @param partitionValue The partition to search
     * @param s1 The prefix of the sort key
     * @return The collection of data
     */
    private Collection<Map<String, AttributeValue>> getByPrefix(AttributeValue partitionValue, AttributeValue s1) {
        // For a prefix, we use the prefix as the inclusive low value, then raise the prefix's last character by
        // one code point and use that as the exclsive high value. This will fail if the last character of the
        // prefix is 0xffff. So don't use strings terminating in unicode 0xffff as prefixes during unit test, OK?
        var terminal = s1.s();
        if (terminal.isEmpty()) {
            // Whoops. Zero length prefix. Everything matches.
            return store.getRange(partitionValue, (AttributeValue)null, true, null, true);
        } else {
            terminal = terminal.substring(0, terminal.length() - 1) + (char)((terminal.charAt(terminal.length() - 1) + 1));
            return store.getRange(partitionValue, s1, true,
                AttributeValue.builder().s(terminal).build(), false);
        }
    }

    /**
     * Return a collection of values that match a comparison operator
     * @param partitionValue The partition to search
     * @param s1 The bound of the comparison
     * @param conditionExpression The expression with the comparison
     * @return The matching data
     * @throws RuntimeException If we cannot parse the key expression
     */
    private Collection<Map<String, AttributeValue>> getByComparator(AttributeValue partitionValue, AttributeValue s1, String conditionExpression) {
        var opMatcher = KEY_EXPRESSION_OP.matcher(conditionExpression);
        if (!opMatcher.matches()) {
            throw new RuntimeException("Cannot parse key expression: " + conditionExpression);
        }
        switch (opMatcher.group(1)) {
            case ">":
                return store.getRange(partitionValue, s1, false, null, false);
            case ">=":
                return store.getRange(partitionValue, s1, true, null, false);
            case "<":
                return store.getRange(partitionValue, null, false, s1, false);
            case "<=":
                return store.getRange(partitionValue, null, false, s1, true);
            default:
                throw new RuntimeException("Cannot parse key expression: " + conditionExpression);
        }
    }

    /**
     * Return a collection of values that are between two sort keys
     * @param partitionValue The partition to search
     * @param s1 The lower bound
     * @param s2 The upper bound
     * @return The matching data
     */
    private Collection<Map<String, AttributeValue>> getByBetween(AttributeValue partitionValue, AttributeValue s1, AttributeValue s2) {
        // Must be a "between" since we have two sort values
        if (AttributeComparator.INSTANCE.compare(s1, s2) > 0) {
            return Collections.emptyList();
        } else {
            return store.getRange(partitionValue, s1, true, s2, true);
        }
    }

    @Override
    public synchronized DeleteItemResponse deleteItem(DeleteItemRequest deleteItemRequest) {
        if (deleteItemRequest.conditionExpression() != null) {
            var expr = new ConditionExpression(deleteItemRequest.conditionExpression(),
                deleteItemRequest.expressionAttributeValues(),
                deleteItemRequest.expressionAttributeNames());
            var prevValue = Optional.ofNullable(store.getItem(deleteItemRequest.key()))
                                .orElse(Collections.emptyMap());
            if (!ExpressionEvaluator.evalBool(expr, prevValue)) {
                throw ConditionalCheckFailedException.builder().build();
            }
        }
        var removed = remove(deleteItemRequest.key());
        return DeleteItemResponse.builder()
            .attributes(deleteItemRequest.returnValues() == ReturnValue.NONE ? null : removed)
            .build();
    }

    /**
     * Remove the item from our backing store. If we find it, we pass the found object on to all child indexes.
     * @param item The keys of the item to remove
     * @return The removed item
     */
    public synchronized Map<String, AttributeValue> remove(Map<String, AttributeValue> item) {
        var result = store.remove(item);
        if (result != null) {
            children.values().forEach(child -> child.remove(result));
        }
        return result;
    }

    @Override
    public synchronized BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
        var response = BatchWriteItemResponse.builder();
        var unprocessed = new ArrayList<WriteRequest>();
        for (var requestTableName: request.requestItems().keySet()) {
            if (!requestTableName.equals(tableInstance.getTableName())) {
                throw new IllegalArgumentException("Got a request for table " + requestTableName + ", this is table " + tableInstance.getTableName());
            }
            for (var write: request.requestItems().get(requestTableName)) {
                if ((write.putRequest() != null) && write.putRequest().hasItem()) {
                    putItem(PutItemRequest.builder().tableName(requestTableName).item(write.putRequest().item()).build());
                }
                if ((write.deleteRequest() != null) && write.deleteRequest().hasKey()) {
                    if (!deleteItem(DeleteItemRequest.builder()
                                       .tableName(requestTableName)
                                       .key(write.deleteRequest().key())
                                       .returnValues(ReturnValue.ALL_OLD)
                                       .build()).hasAttributes()) {
                        unprocessed.add(write);
                    }
                }
            }
        }
        return response.unprocessedItems(Map.of(tableInstance.getTableName(), unprocessed)).build();
    }

    @Override
    public synchronized BatchGetItemResponse batchGetItem(BatchGetItemRequest batchGetItemRequest) {
        var results = new ArrayList<Map<String, AttributeValue>>();
        var unprocessedKeys = new ArrayList<Map<String, AttributeValue>>();
        for (var requestTableName: batchGetItemRequest.requestItems().keySet()) {
            if (!requestTableName.equals(tableInstance.getTableName())) {
                throw new IllegalArgumentException("Got a request for table " + requestTableName + ", this is table " + tableInstance.getTableName());
            }
            for (var key: batchGetItemRequest.requestItems().get(requestTableName).keys()) {
                var item = store.getItem(key);
                if (item == null) {
                    unprocessedKeys.add(key);
                } else {
                    results.add(item);
                }
            }
        }
        return BatchGetItemResponse.builder()
                   .responses(Map.of(tableInstance.getTableName(), results))
                   .unprocessedKeys(Map.of(tableInstance.getTableName(), KeysAndAttributes.builder().keys(unprocessedKeys).build()))
                   .build();
    }

    @Override
    public synchronized UpdateItemResponse updateItem(UpdateItemRequest request) {
        var prevItem = Optional.ofNullable(store.getItem(request.key())).orElse(Collections.emptyMap());
        if (request.conditionExpression() != null) {
            if (!ExpressionEvaluator.evalBool(new ConditionExpression(request.conditionExpression(),
                request.expressionAttributeValues(), request.expressionAttributeNames()), prevItem)) {
                var ex = ConditionalCheckFailedException.builder();
                if (!prevItem.isEmpty()) {
                    ex.item(prevItem);
                }
                throw ex.build();
            }
        }
        var curItem = ExpressionEvaluator.update(request.updateExpression(), prevItem,
            request.expressionAttributeValues(), request.expressionAttributeNames());
        curItem.putAll(request.key());  // The key is always kept unchanged, added if we are creating the item
        store.add(curItem);
        children.values().forEach(child -> child.store.remove(prevItem));
        children.values().forEach(child -> child.store.add(curItem));
        var response = UpdateItemResponse.builder();
        var returnItem = (request.returnValues() == ReturnValue.ALL_OLD ? prevItem : curItem);
        if (!returnItem.isEmpty()) {
            response.attributes(returnItem);
        }
        return response.build();
    }

    @Override
    public TransactWriteItemsResponse transactWriteItems(TransactWriteItemsRequest request) {
        var statements = request.transactItems();

        // This is used to find all tables we must lock. The key is the table name, so if we see the same table
        // multiple times, we'll only include it once (and only lock it once). As a tree map it will keep things
        // ordered by the key, which gives us an ordering of tables that we must adhere to whenever we lock more
        // than one table at a time.
        var tableToClient = new TreeMap<String, MockDynamoClient>();

        // First step: Extract everything we need to lock our tables and run our condition checks. The system where
        // we need to check each type of statement and have identical code blocks in each to extract data is so
        // clumsy that it makes sense to do it once, here, and pull out the data we need. When we actually execute
        // the statements we'll have to do it again to do the actual execution, but that can't be avoided

        var clientList = new ArrayList<MockDynamoClient>();
        var conditionList = new ArrayList<ConditionExpression>();
        var keyList = new ArrayList<Map<String, AttributeValue>>();
        synchronized (TABLE_TO_MOCK_CLIENT) {
            for (var statement : statements) {
                String tableName, expression;
                Map<String, AttributeValue> values;
                Map<String, String> names;
                Map<String, AttributeValue> key;
                if (statement.put() != null) {
                    tableName = statement.put().tableName();
                    expression = statement.put().conditionExpression();
                    values = statement.put().expressionAttributeValues();
                    names = statement.put().expressionAttributeNames();
                    key = statement.put().item();
                } else if (statement.delete() != null) {
                    tableName = statement.delete().tableName();
                    expression = statement.delete().conditionExpression();
                    values = statement.delete().expressionAttributeValues();
                    names = statement.delete().expressionAttributeNames();
                    key = statement.delete().key();
                } else if (statement.update() != null) {
                    tableName = statement.update().tableName();
                    expression = statement.update().conditionExpression();
                    values = statement.update().expressionAttributeValues();
                    names = statement.update().expressionAttributeNames();
                    key = statement.update().key();
                } else if (statement.conditionCheck() != null) {
                    tableName = statement.conditionCheck().tableName();
                    expression = statement.conditionCheck().conditionExpression();
                    values = statement.conditionCheck().expressionAttributeValues();
                    names = statement.conditionCheck().expressionAttributeNames();
                    key = statement.conditionCheck().key();
                } else {
                    throw new IllegalArgumentException("Unknown operation: " + statement);
                }
                var client = Optional.ofNullable(TABLE_TO_MOCK_CLIENT.get(tableName))
                    .orElseThrow(() -> new RuntimeException("Table " + tableName + " not found; known tables are "
                        + String.join(", ", TABLE_TO_MOCK_CLIENT.keySet())))
                    .get();
                tableToClient.put(tableName, client);
                clientList.add(client);
                conditionList.add(expression == null ? null : new ConditionExpression(expression, values, names));
                keyList.add(key);
            }
        }
        lockAndInvokeTransaction(new ArrayList<>(tableToClient.values()), statements, clientList, conditionList,
            keyList);
        return TransactWriteItemsResponse.builder().build();  // Nothing useful in return value
    }

    /**
     * Recursively lock all tables needed, then call {@link #invokeTransaction(List, List, List, List)}. This must be
     * done recursively because Java's only way to lock on objects is to do so in a synchronized block. We will lock
     * the unsynchronizedClients list in order, from element 0 to the last element. The list is emptied in the process
     * @param unsynchronizedClients The mock dynamo clients that we still need to synchronize on. The head of this
     *                              list is stripped out in each iteration until we have an empty list, then we know
     *                              it is time to call invokeTrasnaction.
     * @param statements The statements to execute
     * @param clientList The mock dynamo clients that can execute the statements0
     * @param conditionList The conditions to test for each statement
     * @param keyList The keys to the objects referred to by each statement
     */
    private void lockAndInvokeTransaction(List<MockDynamoClient> unsynchronizedClients, List<TransactWriteItem> statements, List<MockDynamoClient> clientList, List<ConditionExpression> conditionList,
                                          List<Map<String, AttributeValue>> keyList) {
        if (unsynchronizedClients.isEmpty()) {
            invokeTransaction(statements, clientList, conditionList, keyList);
        } else {
            synchronized (unsynchronizedClients.remove(0)) {
                lockAndInvokeTransaction(unsynchronizedClients, statements, clientList, conditionList, keyList);
            }
        }
    }

    /**
     * Invoke a transaction. This is called after all tables involved are already locked. First we run all condition
     * expressions, then if they all pass, we execute the write part of the statements. The parameters are all
     * lists, and the order is important; statement 5, for example, goes with client 5, condition 5, and key 5.
     * @param statements The list of statements to execute
     * @param clientList The list of mock dynamo clients that match the tables referred to by the statements
     * @param conditionList A list of condition expressions. May be null for statements with no condition
     * @param keyList A list of the keys for the items affected. This is used to fetch the items for the condition
     *                evaluations
     * @throws TransactionCanceledException If one or more condition expressions failed
     */
    private void invokeTransaction(List<TransactWriteItem> statements, List<MockDynamoClient> clientList, List<ConditionExpression> conditionList,
                           List<Map<String, AttributeValue>> keyList) {
        var cancelled = false;
        var cancellationReasons = new ArrayList<CancellationReason>();
        for (int i = 0; i < clientList.size(); ++i) {
            var reason = SUCCESS_REASON;
            var cond = conditionList.get(i);
            if (cond != null) {
                var client = clientList.get(i);
                var prevItem = client.store.getItem(keyList.get(i));
                if (!ExpressionEvaluator.evalBool(cond, prevItem)) {
                    cancelled = true;
                    reason = CancellationReason.builder()
                                 .code("ConditionalCheckFailed")
                                 .item(prevItem)
                                 .build();
                }
            }
            cancellationReasons.add(reason);
        }
        if (cancelled) {
            throw TransactionCanceledException.builder()
                      .cancellationReasons(cancellationReasons)
                      .build();
        }
        for (int i = 0; i < clientList.size(); ++i) {
            var statement = statements.get(i);
            if (statement.put() != null) {
                clientList.get(i).store.add(statement.put().item());
            } else if (statement.delete() != null) {
                clientList.get(i).store.remove(statement.delete().key());
            } else if (statement.update() != null) {
                // Rather than re-implement update, we copy the transaction update statement into an UpdateItemRequest,
                // leaving out the condition (which is already evaluated), then execute the UpdateItemRequest.
                var update = statement.update();
                var request = UpdateItemRequest.builder().key(update.key()).updateExpression(update.updateExpression());
                if (update.hasExpressionAttributeNames()) {
                    request = request.expressionAttributeNames(update.expressionAttributeNames());
                }
                if (update.hasExpressionAttributeValues()) {
                    request = request.expressionAttributeValues(update.expressionAttributeValues());
                }
                clientList.get(i).updateItem(request.build());
            }
        }
    }

    /**
     * Build an index
     * @param name The name of the index
     * @return A mock client that will work as a backer for the specified index
     */
    private MockDynamoClient buildIndex(String name) {
        var allObjects = store.getItems().collect(Collectors.toList());
        return new MockDynamoClient(tableInstance, tableInstance.getIndex(name, null, null), allObjects);
    }

    @Override
    public void close() {
        // Do nothing
    }

    /**
     * Convert an item to a partition/sort key
     * @param item The item
     * @return The key that will match the item
     */
    public synchronized Map<String, AttributeValue> toKey(Map<String, AttributeValue> item) {
        return item.entrySet().stream()
                   .filter(e -> e.getKey().equals(tableInstance.getPartitionKeyAttribute()) || e.getKey().equals(tableInstance.getSortKeyAttribute()))
                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
