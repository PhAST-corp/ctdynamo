package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
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
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A client that uses DynamoIndex and DynamoTable objects to emulate an in-memory Dynamo database
 */
class MockDynamoClient implements DynamoDbClient {

    /** Recognizes the key expressions that ctDynamo generates */
    private static final Pattern KEY_EXPRESSION_OP = Pattern.compile(".*?([<>=]+) *:ctdynamo_s1.*");

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
    public GetItemResponse getItem(GetItemRequest getItemRequest) {
        return GetItemResponse.builder()
                   .item(store.getItem(getItemRequest.key()))
                   .build();
    }

    @Override
    public synchronized PutItemResponse putItem(PutItemRequest putItemRequest) {
        var exprString = putItemRequest.conditionExpression();
        if (exprString != null) {
            var expr = new Expression(exprString, putItemRequest.expressionAttributeValues(), putItemRequest.expressionAttributeNames());
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
            var expr = new Expression(request.filterExpression(), request.expressionAttributeValues(), request.expressionAttributeNames());
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
            System.out.println("WARNING: invalid range, low(" + s1 + ") > high(" + s2 + ")");
            return Collections.emptyList();
        } else {
            return store.getRange(partitionValue, s1, true, s2, true);
        }
    }

    @Override
    public DeleteItemResponse deleteItem(DeleteItemRequest deleteItemRequest) {
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
    public Map<String, AttributeValue> remove(Map<String, AttributeValue> item) {
        var result = store.remove(item);
        if (result != null) {
            children.values().forEach(child -> child.remove(result));
        }
        return result;
    }

    @Override
    public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
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
    public BatchGetItemResponse batchGetItem(BatchGetItemRequest batchGetItemRequest) {
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
    public Map<String, AttributeValue> toKey(Map<String, AttributeValue> item) {
        return item.entrySet().stream()
                   .filter(e -> e.getKey().equals(tableInstance.getPartitionKeyAttribute()) || e.getKey().equals(tableInstance.getSortKeyAttribute()))
                   .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
