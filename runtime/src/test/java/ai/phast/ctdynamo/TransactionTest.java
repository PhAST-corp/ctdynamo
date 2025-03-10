package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.Map;

public class TransactionTest {

    /**
     * The same request is used twice. This is the transaction that it should be compiled into
     */
    private static final TransactWriteItemsRequest EXPECTED_TRANSACTION = TransactWriteItemsRequest.builder()
        .transactItems(
            TransactWriteItem.builder()
                .put(Put.builder()
                    .tableName("table2")
                    .item(Map.of("partition", av("c"), "sort", av("cookies"), "ival", av(10)))
                    .conditionExpression("attribute_not_exists(#partition)")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build()).build(),
            TransactWriteItem.builder()
                .update(Update.builder()
                    .tableName("table1")
                    .key(Map.of("partition", av("a"), "sort", av("chips")))
                    .updateExpression("SET #ival = :ten")
                    .expressionAttributeValues(Map.of(":ten", av(10)))
                    .expressionAttributeNames(Map.of("#ival", "ival"))
                    .build()).build(),
            TransactWriteItem.builder()
                .delete(Delete.builder()
                    .tableName("table2")
                    .key(Map.of("partition", av("a"), "sort", av("pie")))
                    .conditionExpression("attribute_exists(#partition)")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build()).build(),
            TransactWriteItem.builder()
                .conditionCheck(ConditionCheck.builder()
                    .tableName("table1")
                    .conditionExpression("ival = :three")
                    .key(Map.of("partition", av("b"), "sort", av("pumpkin")))
                    .expressionAttributeValues(Map.of(":three", av(3)))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build()).build())
        .build();

    @Test
    public void testTransaction_shouldBuildCommandAndReturnNoErrors_whenResponseIsNoErrors() {
        // Setup
        var mockClient = new MockClient(EXPECTED_TRANSACTION,
            TransactWriteItemsResponse.builder().build());
        var table1 = new MockTable(mockClient, null, "table1");
        var table2 = new MockTable(mockClient, null, "table2");
        var transaction = new Transaction(table1);
        var putCheck = transaction.put(table2, new MockItem("c", "cookies", 10),
            ConditionExpression.requireAbsent(table2));
        var updateCheck = transaction.update(table1, "a", "chips", null,
            Map.of("ival", ":ten"),
            Map.of(":ten", av(10)),
            null);
        var deleteCheck = transaction.delete(table2, "a", "pie",
            ConditionExpression.requirePresent(table2));
        var checkCheck = transaction.check(table1, "b", "pumpkin",
            new ConditionExpression("ival = :three", Map.of(":three", av(3)),
                null));

        // Act & verify request
        transaction.execute();

        // Verify responses
        Assertions.assertNull(putCheck.getErrorType());
        Assertions.assertNull(updateCheck.getErrorType());
        Assertions.assertNull(deleteCheck.getErrorType());
        Assertions.assertNull(checkCheck.getErrorType());
    }

    @Test
    public void testTransaction_shouldStoreErrors_whenResponseHasErrors() {
        // Setup
        var mockClient = new MockClient(EXPECTED_TRANSACTION,
            TransactionCanceledException.builder()
                .cancellationReasons(
                    CancellationReason.builder()
                        .code("None")
                        .build(),
                    CancellationReason.builder()
                        .code("Capacity")
                        .build(),
                    CancellationReason.builder()
                        .code("ConditionalCheckFailed")
                        .item(Map.of("partition", av("a"), "sort", av("pie"), "ival", av(4)))
                        .build(),
                    CancellationReason.builder()
                        .code("None")
                        .build())
                .build());
        var table1 = new MockTable(mockClient, null, "table1");
        var table2 = new MockTable(mockClient, null, "table2");
        var transaction = new Transaction(table1);
        var putCheck = transaction.put(table2, new MockItem("c", "cookies", 10),
            ConditionExpression.requireAbsent(table2));
        var updateCheck = transaction.update(table1, "a", "chips", null,
            Map.of("ival", ":ten"),
            Map.of(":ten", av(10)),
            null);
        var deleteCheck = transaction.delete(table2, "a", "pie",
            ConditionExpression.requirePresent(table2));
        var checkCheck = transaction.check(table1, "b", "pumpkin",
            new ConditionExpression("ival = :three", Map.of(":three", av(3)),
                null));

        // Act & verify request
        Assertions.assertThrows(TransactionCanceledException.class, transaction::execute);

        // Verify responses
        Assertions.assertNull(putCheck.getErrorType());
        Assertions.assertEquals(Transaction.ErrorType.OTHER, updateCheck.getErrorType());
        Assertions.assertEquals(Transaction.ErrorType.CONDITION_CHECK, deleteCheck.getErrorType());
        Assertions.assertEquals(new MockItem("a", "pie", 4), deleteCheck.getFailedItem());
        Assertions.assertNull(checkCheck.getErrorType());
    }

    private static AttributeValue av(String value) {
        return DynamoTableTest.av(value);
    }

    private static AttributeValue av(int value) {
        return DynamoTableTest.av(value);
    }
}
