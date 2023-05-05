package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QueryTest {

    @Test
    public void testQuery_shouldReturnNothing_whenEmptyResponse() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
        client.assertDone();
    }

    @Test
    public void testQuery_shouldThrow_whenNoPartitionProvided() {
        // Setup
        var client = new MockClient();
        var table = new MockTable(client, null, "mock");

        // Act & verify
        Assertions.assertThrows(IllegalStateException.class, () -> table.query().invoke());
    }

    @Test
    public void testQuery_shouldBuildKeyQuery_whenLowerSortBound() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p AND #sort>:ctdynamo_s1")
                .expressionAttributeNames(Map.of("#partition", "partition", "#sort", "sort"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p"), ":ctdynamo_s1", av("x")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").sortGreaterThan("x").invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
        client.assertDone();
    }

    @Test
    public void testQuery_shouldBuildKeyQuery_whenUpperAndLowerSortBound() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p AND #sort BETWEEN :ctdynamo_s1 AND :ctdynamo_s2")
                .expressionAttributeNames(Map.of("#partition", "partition", "#sort", "sort"))
                .expressionAttributeValues(Map.of(
                    ":ctdynamo_p", av("p"),
                    ":ctdynamo_s1", av("a"),
                    ":ctdynamo_s2", av("z")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").sortBetween("a", "z").invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
        client.assertDone();
    }

    @Test
    public void testQuery_shouldBuildKeyQuery_whenUpperBound() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p AND #sort<=:ctdynamo_s1")
                .expressionAttributeNames(Map.of("#partition", "partition", "#sort", "sort"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p"), ":ctdynamo_s1", av("x")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query().lessThanOrEqual(new MockItem("p", "x", 80)).invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
        client.assertDone();
    }

    @Test
    public void testQuery_shouldReturnItemsFromAllPages_whenResultIsMultipage() {
        // Setup
        var client = new MockClient(
            List.of(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", DynamoTableTest.av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                        Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build(),
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("sssss"), "ival", av(789))))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456),
            new MockItem("p", "sssss", 789)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldReturnExclusiveStart_whenLimitIsReached() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .limit(2)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").limit(2).invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,sss", result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldCutResponseShort_whenLimitIsExceeded() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .limit(1)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").limit(1).invoke();

        // Verify
        Assertions.assertEquals(List.of(new MockItem("p", "s", 123)), result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s", result.getExclusiveStartKey());
    }

    @Test
    public void testQueryAsync_shouldReturnNothing_whenEmptyResponse() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", DynamoTableTest.av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.queryAsync("p").invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testQueryAsync_shouldReturnItemsFromAllPages_whenResultIsMultipage() {
        // Setup
        var client = new MockClient(
            List.of(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .expressionAttributeNames(Map.of("#partition", "partition"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", DynamoTableTest.av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                        Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build(),
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("sssss"), "ival", av(789))))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.queryAsync("p").invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456),
            new MockItem("p", "sssss", 789)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testQueryAsync_shouldReturnExclusiveStart_whenLimitIsReached() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .limit(2)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.queryAsync("p").limit(2).invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,sss", result.getExclusiveStartKey());
    }

    @Test
    public void testQueryAsync_shouldCutResponseShort_whenLimitIsExceeded() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .limit(1)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .scannedCount(1)
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.queryAsync("p").limit(1).invoke();

        // Verify
        Assertions.assertEquals(List.of(new MockItem("p", "s", 123)), result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s", result.getExclusiveStartKey());
    }

    @Test
    public void testQueryAsync_shouldMakeBiggerQueries_whenResultsAreEmpty() {
        // Setup
        var client = new MockClient(
            List.of(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(2)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(4)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("sss")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                QueryResponse.builder()
                    .items(List.of())
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                    .scannedCount(2)
                    .build(),
                QueryResponse.builder()
                    .items(List.of())
                    .scannedCount(4)
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.queryAsync("p")
            .limit(1)
            .filter(ConditionExpression.requirePresent("foo"))
            .invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldGrowCorrectly_whenResultsAreIncomplete() {
        // Setup
        var client = new MockClient(
            List.of(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(8)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(6)  // We got 3 in a query of 8, so now we'll query 6 to get the last one
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s8")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(12)  // Our query of 6 got none, so now we'll go to 12
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s9")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s1"), "ival", av(1)),
                        Map.of("partition", av("p"), "sort", av("s2"), "ival", av(1)),
                        Map.of("partition", av("p"), "sort", av("s3"), "ival", av(1))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s8")))
                    .scannedCount(8)
                    .build(),
                QueryResponse.builder()
                    .items(List.of())
                    .scannedCount(6)
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s9")))
                    .build(),
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s4"), "ival", av(1)),
                        Map.of("partition", av("p"), "sort", av("s5"), "ival", av(1))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s12")))
                    .scannedCount(12)
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p")
            .limit(4)
            .filter(ConditionExpression.requirePresent("foo"))
            .invoke();

        // Verify
        Assertions.assertEquals(List.of(
                new MockItem("p", "s1", 1),
                new MockItem("p", "s2", 1),
                new MockItem("p", "s3", 1),
                new MockItem("p", "s4", 1)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s4", result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldStopQueries_whenReadLimitReached() {
        // Setup
        var client = new MockClient(
            List.of(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(8)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(5)  // Algorithm wants to read 6 rows to get the last item, but read limit 13 cuts it to 5
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s8")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                QueryResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s1"), "ival", av(1)),
                        Map.of("partition", av("p"), "sort", av("s2"), "ival", av(1)),
                        Map.of("partition", av("p"), "sort", av("s3"), "ival", av(1))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s8")))
                    .scannedCount(8)
                    .build(),
                QueryResponse.builder()
                    .items(List.of())
                    .scannedCount(5)
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s9")))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p")
            .limit(4)
            .readLimit(13)
            .filter(ConditionExpression.requirePresent("foo"))
            .invoke();

        // Verify
        Assertions.assertEquals(List.of(
                new MockItem("p", "s1", 1),
                new MockItem("p", "s2", 1),
                new MockItem("p", "s3", 1)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s9", result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldDoSmallPage_whenReadLimitBelowPageSizeWithFilter() {
        // Setup
        var client = new MockClient(
                QueryRequest.builder()
                    .tableName("mock")
                    .scanIndexForward(true)
                    .limit(5)
                    .keyConditionExpression("#partition = :ctdynamo_p")
                    .filterExpression("attribute_exists(#foo)")
                    .expressionAttributeNames(Map.of("#partition", "partition", "#foo", "foo"))
                    .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                QueryResponse.builder()
                    .items(List.of(Map.of("partition", av("p"), "sort", av("s1"), "ival", av(1))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s8")))
                    .scannedCount(5)
                    .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p")
            .pageSize(20)
            .readLimit(5)
            .filter(ConditionExpression.requirePresent("foo"))
            .invoke();

        // Verify
        Assertions.assertEquals(List.of(
                new MockItem("p", "s1", 1)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s8", result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldDoSmallPage_whenReadLimitBelowPageSizeWithoutFilter() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .limit(5)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s1"), "ival", av(1)),
                    Map.of("partition", av("p"), "sort", av("s2"), "ival", av(1)),
                    Map.of("partition", av("p"), "sort", av("s3"), "ival", av(1)),
                    Map.of("partition", av("p"), "sort", av("s4"), "ival", av(1)),
                    Map.of("partition", av("p"), "sort", av("s5"), "ival", av(1))
                ))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s5")))
                .scannedCount(5)
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p")
            .pageSize(20)
            .readLimit(5)
            .invoke();

        // Verify
        Assertions.assertEquals(List.of(
                new MockItem("p", "s1", 1),
                new MockItem("p", "s2", 1),
                new MockItem("p", "s3", 1),
                new MockItem("p", "s4", 1),
                new MockItem("p", "s5", 1)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s5", result.getExclusiveStartKey());
    }

    @Test
    public void testQuery_shouldDecodeStartKey_whenSet() {
        // Setup
        var client = new MockClient(
            QueryRequest.builder()
                .tableName("mock")
                .scanIndexForward(true)
                .keyConditionExpression("#partition = :ctdynamo_p")
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .expressionAttributeValues(Map.of(":ctdynamo_p", av("p")))
                .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .build(),
            QueryResponse.builder().items(List.of()).build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.query("p").exclusiveStartKey("p,s").invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
    }

    private static AttributeValue av(String value) {
        return DynamoTableTest.av(value);
    }

    private static AttributeValue av(int value) {
        return DynamoTableTest.av(value);
    }
}
