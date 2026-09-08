package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Perform a DynamoDB write transaction. If any single statement fails, then no changes are made to the database
 */
public class Transaction {

    /** Enum returned when checkin status of a single statement in the transaction */
    public enum ErrorType {
        /** Condition expression of the statement failed. Item is present */
        CONDITION_CHECK,

        /** Insufficient throughput on this table */
        THROUGHPUT,

        /** Statement has an error */
        VALIDATION,

        /** Conflict with another transaction or write operation to the table */
        CONFLICT,

        /** Unknown error code */
        OTHER
    }

    /** String constant used in dynamo responses */
    private static final Map<String, ErrorType> CODE_TO_ERROR_TYPE = new HashMap<>();
    static {
        // Must fill out the map here, instead of "Map.of(...)", because we want a null for no error and an OTHER
        // for any unknown code
        CODE_TO_ERROR_TYPE.put("ConditionalCheckFailed", ErrorType.CONDITION_CHECK);
        CODE_TO_ERROR_TYPE.put("None", null);
        CODE_TO_ERROR_TYPE.put("ThrottlingError", ErrorType.THROUGHPUT);
        CODE_TO_ERROR_TYPE.put("ProvisionedThroughputExceeded", ErrorType.THROUGHPUT);
        CODE_TO_ERROR_TYPE.put("ValidationError", ErrorType.VALIDATION);
        CODE_TO_ERROR_TYPE.put("TransactionConflict", ErrorType.CONFLICT);
    }

    /** Client used to perform operation */
    private final DynamoDbClient client;

    /** Client used to perform operation */
    private final DynamoDbAsyncClient asyncClient;

    /** The statements that go into our transaction */
    private final List<TransactWriteItem> statements = new ArrayList<>();

    /** Have we been executed yet? */
    private boolean executed = false;

    /** If we are cancelled, we store the per-operation cancellation reasons here */
    private List<CancellationReason> cancellationReasons;

    /**
     * Construct a transaction from a Dynamo client
     * @param client Our dynamo client
     */
    public Transaction(DynamoDbClient client) {
        this.client = client;
        this.asyncClient = null;
    }

    /**
     * Construct a transaction from a Dynamo async client
     * @param asyncClient The client
     */
    public Transaction(DynamoDbAsyncClient asyncClient) {
        this.client = null;
        this.asyncClient = asyncClient;
    }

    /**
     * Construct a transaction using the same client as the given table
     * @param table The table whose dynamo client we will use
     */
    public Transaction(DynamoTable<?, ?, ?> table) {
        client = table.getClient();
        asyncClient = table.getAsyncClient();
    }

    /**
     * Delete an entry from a table
     * @param table The table to modify
     * @param item The item to remove. Only the partition and sort values are used
     * @param conditionExpression Optional condition expression to run before the statement
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> delete(
        DynamoTable<ItemT, PartitionT, SortT> table, ItemT item, ConditionExpression conditionExpression) {

        return delete(table, table.getPartitionValue1(item), table.getSortValue(item), conditionExpression);
    }

    /**
     * Delete an entry from a table
     * @param table The table to modify
     * @param key The partition and sort keys of the element
     * @param conditionExpression Optional condition expression to run before the statement
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> delete(
        DynamoTable<ItemT, PartitionT, SortT> table, Key<PartitionT, Void, Void, Void, SortT> key,
        ConditionExpression conditionExpression) {

        return delete(table, key.getPartition1(), key.getSort(), conditionExpression);
    }

    /**
     * Delete an entry from a table
     * @param table The table to modify
     * @param partitionValue The partition value of the element to remove
     * @param sortValue The sort value of the element to remove
     * @param conditionExpression Optional condition expression to run before the statement
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> delete(
        DynamoTable<ItemT, PartitionT, SortT> table, PartitionT partitionValue, SortT sortValue,
        ConditionExpression conditionExpression) {

        var delete = Delete.builder()
                         .tableName(table.getTableName())
                         .key(table.keysToMap(partitionValue, sortValue));
        if (conditionExpression != null) {
            delete = delete.conditionExpression(conditionExpression.getExpression())
                         .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD);
            if (conditionExpression.getAttributeNames() != null) {
                delete = delete.expressionAttributeNames(conditionExpression.getAttributeNames());
            }
            if (conditionExpression.getValues() != null) {
                delete = delete.expressionAttributeValues(conditionExpression.getValues());
            }
        }
        statements.add(TransactWriteItem.builder()
                           .delete(delete.build())
                           .build());
        return new FailureCheck<>(statements.size() - 1, table);
    }

    /**
     * Update an object in a table. See
     * {@link DynamoTable#updateItem(Object, ConditionExpression, Map, Map, Map, boolean)} for more information.
     * @param table The table to modify
     * @param item The item to write. By default, all values in the item will overwrite what is in the table
     * @param conditionExpression Optional condition expression to run before the statement
     * @param expressions Optional additional modifications to make, overriding what is in the item
     * @param values Values used by the expressions
     * @param attributeNames Attribute names used by the expressions
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> update(
        DynamoTable<ItemT, PartitionT, SortT> table, ItemT item,
        ConditionExpression conditionExpression, Map<String, String> expressions, Map<String, AttributeValue> values,
        Map<String, String> attributeNames) {

        var expressionsCopy = expressions == null
                              ? new HashMap<String, String>()
                              : expressions.entrySet().stream()
                                    // If we are ignoring the attribute, don't add it to the expression map.
                                    .filter(entry -> !DynamoTable.UPDATE_IGNORE_ATTRIBUTE.equals(entry.getValue()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var valuesCopy = new HashMap<String, AttributeValue>();
        var namesCopy = new HashMap<String, String>();
        for (var entry : table.encode(item).entrySet()) {
            var attributeName = entry.getKey();
            // Do not add the attribute to the value map if it is one of the keys,
            // or if that attribute is specifically passed in in the expression map
            if (!attributeName.equals(table.getPartitionKeyAttributes().get(0))
                    && !attributeName.equals(table.getSortKeyAttribute())
                    && (expressions == null || !expressions.containsKey(attributeName))) {
                var valueRef = ":" + attributeName;
                expressionsCopy.put(attributeName, valueRef);
                valuesCopy.put(valueRef, entry.getValue());
            }
        }
        if (values != null) {
            valuesCopy.putAll(values);
        }
        if (attributeNames != null) {
            namesCopy.putAll(attributeNames);
        }
        return update(table, table.getPartitionValue1(item), table.getSortValue(item),
            conditionExpression, expressionsCopy, valuesCopy, namesCopy);
    }

    /**
     * Update an object in a table. See
     * {@link DynamoTable#updateItem(Object, ConditionExpression, Map, Map, Map, boolean)} for more information.
     * @param table The table to modify
     * @param conditionExpression Optional condition expression to run before the statement
     * @param expressions Optional additional modifications to make, overriding what is in the item
     * @param values Values used by the expressions
     * @param attributeNames Attribute names used by the expressions
     * @param partitionValue The value of the partition key
     * @param sortValue The value of the sort key
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> update(
        DynamoTable<ItemT, PartitionT, SortT> table, PartitionT partitionValue, SortT sortValue,
        ConditionExpression conditionExpression, Map<String, String> expressions, Map<String, AttributeValue> values,
        Map<String, String> attributeNames) {

        // Copy attribute names so we can mutate it
        attributeNames = attributeNames == null
            ? new HashMap<>()
            : new HashMap<>(attributeNames);
        var update = Update.builder()
                         .tableName(table.getTableName())
                         .key(table.keysToMap(partitionValue, sortValue));
        var expression = new StringBuilder();
        List<String> removals = null;
        var prefix = "SET ";
        for (var entry : expressions.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var nameRef = '#' + key;
            attributeNames.put(nameRef, key);
            if (value.equals(DynamoTable.UPDATE_REMOVE_ATTRIBUTE)) {
                // We need to remove this value
                if (removals == null) {
                    removals = new ArrayList<>();
                }
                removals.add(key);
            } else {
                expression.append(prefix)
                    .append(nameRef)
                    .append(" = ")
                    .append(value);
                prefix = ", ";
            }
        }
        if (removals != null) {
            prefix = " REMOVE #";
            for (var removal : removals) {
                expression.append(prefix).append(removal);
                prefix = ", #";
            }
        }
        if (conditionExpression != null) {
            if (conditionExpression.getValues() != null) {
                // Copy values since we are about to mutate it
                values = values == null
                    ? new HashMap<>()
                    : new HashMap<>(values);
                values.putAll(conditionExpression.getValues());
            }
            if (conditionExpression.getAttributeNames() != null) {
                attributeNames.putAll(conditionExpression.getAttributeNames());
            }
            update.conditionExpression(conditionExpression.getExpression())
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD);
        }
        update.updateExpression(expression.toString())
            .expressionAttributeNames(attributeNames);
        if (values != null && !values.isEmpty()) {
            update.expressionAttributeValues(values);
        }
        statements.add(TransactWriteItem.builder()
                           .update(update.build())
                           .build());
        return new FailureCheck<>(statements.size() - 1, table);
    }

    /**
     * Write an object to a table.
     * @param table The table to modify
     * @param item The item to write.
     * @param conditionExpression Optional condition expression to run before the statement
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> put(
        DynamoTable<ItemT, PartitionT, SortT> table, ItemT item, ConditionExpression conditionExpression) {

        var put = Put.builder()
                      .tableName(table.getTableName())
                      .item(table.encode(item));
        if (conditionExpression != null) {
            put = put.conditionExpression(conditionExpression.getExpression())
                      .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD);
            if (conditionExpression.getAttributeNames() != null) {
                put = put.expressionAttributeNames(conditionExpression.getAttributeNames());
            }
            if (conditionExpression.getValues() != null) {
                put = put.expressionAttributeValues(conditionExpression.getValues());
            }
        }
        statements.add(TransactWriteItem.builder()
                           .put(put.build())
                           .build());
        return new FailureCheck<>(statements.size() - 1, table);
    }

    /**
     * Add a conditional statement to a transaction that does no modifications to the row
     * @param table The table to modify
     * @param conditionExpression Condition expression to run
     * @param partitionValue The value of the partition key
     * @param sortValue The value of the sort key
     * @return A value that lets us check the error status of this statement if the whole operation is cancelled
     * @param <ItemT> The type of item in the table
     * @param <PartitionT> The type of the partition key of the table
     * @param <SortT> The type of the sort key of the table
     */
    public <ItemT, PartitionT, SortT> FailureCheck<ItemT> check(
        DynamoTable<ItemT, PartitionT, SortT> table, PartitionT partitionValue, SortT sortValue,
        ConditionExpression conditionExpression) {
        var check = ConditionCheck.builder()
                        .tableName(table.getTableName())
                        .key(table.keysToMap(partitionValue, sortValue))
                        .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                        .conditionExpression(conditionExpression.getExpression());
        if (conditionExpression.getAttributeNames() != null) {
            check = check.expressionAttributeNames(conditionExpression.getAttributeNames());
        }
        if (conditionExpression.getValues() != null) {
            check = check.expressionAttributeValues(conditionExpression.getValues());
        }
        statements.add(TransactWriteItem.builder()
                           .conditionCheck(check.build())
                           .build());
        return new FailureCheck<>(statements.size() - 1, table);
    }

    /**
     * Execute the transaction
     * @throws TransactionCanceledException if the transaction was cancelled by one of the table operations
     * @throws IllegalStateException If the transaction has already been executed
     */
    public void execute() {
        if (executed) {
            throw new IllegalStateException("Transaction has already executed");
        }
        try {
            executed = true;
            var command = TransactWriteItemsRequest.builder()
                              .transactItems(statements)
                              .build();
            if (client == null) {
                asyncClient.transactWriteItems(command).join();
            } else {
                client.transactWriteItems(command);
            }
        } catch (TransactionCanceledException exception) {
            cancellationReasons = exception.cancellationReasons();
            throw exception;
        }
    }

    /**
     * An object that lets you check on the status of a statement if the transaction is cancelled. Using this you can
     * tell which statement failed the condition check, and (for that statement) see the object stored in the database.
     * @param <T> The type of the object stored in the relevant table
     */
    public class FailureCheck<T> {

        /** Index of the item in our transaction's statement list */
        private final int index;

        /** The table that was involved */
        private final DynamoTable<T, ?, ?> table;

        /** We set this to true when we have checked the result of this object */
        private boolean checked;

        /** The type of error we got in this statement. null if there is no error */
        private ErrorType errorType;

        /** The item returned in the error. Only returned when we failed the condition check */
        private T item;

        /**
         * Construct a failure check
         * @param index The index of our statement in the transaction
         * @param table The table that was involved
         */
        private FailureCheck(int index, DynamoTable<T, ?, ?> table) {
            this.index = index;
            this.table = table;
        }

        /**
         * Get the error type of our statement
         * @return The error type, or null if there was no error for this statement
         * @throws IllegalStateException If our transaction has not executed yet
         */
        public ErrorType getErrorType() {
            check();
            return errorType;
        }

        /**
         * Get the item that failed the condition check. This will be null unless our error type is
         * {@link ErrorType#CONDITION_CHECK}
         * @return The objet that failed the condition check
         * @throws IllegalStateException If our transaction has not executed yet
         */
        public T getFailedItem() {
            check();
            return item;
        }

        /**
         * Fill out our error type and item fields
         */
        private void check() {
            if (checked) {
                return;
            }
            if (!executed) {
                throw new IllegalStateException("Transaction has not executed yet");
            }
            checked = true;
            if (cancellationReasons != null) {
                var reason = cancellationReasons.get(index);
                errorType = CODE_TO_ERROR_TYPE.getOrDefault(reason.code(), ErrorType.OTHER);
                if (reason.hasItem()) {
                    item = table.decode(reason.item());
                }
            }
        }
    }
}