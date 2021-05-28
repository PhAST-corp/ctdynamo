package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScanTest {

    @Test
    public void testScan_shouldReturnNothing_whenEmptyResponse() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .totalSegments(1)
                .segment(0)
                .build(),
            ScanResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scan().invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testScan_shouldReturnItemsFromAllPages_whenResultIsMultipage() {
        // Setup
        var client = new MockClient(
            List.of(
                ScanRequest.builder()
                    .tableName("mock")
                    .totalSegments(1)
                    .segment(0)
                    .build(),
                ScanRequest.builder()
                    .tableName("mock")
                    .totalSegments(1)
                    .segment(0)
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build()),
            List.of(
                ScanResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                        Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build(),
                ScanResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("sssss"), "ival", av(789))))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scan().invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456),
            new MockItem("p", "sssss", 789)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testScan_shouldReturnExclusiveStart_whenLimitIsReached() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .segment(0)
                .totalSegments(1)
                .limit(2)
                .build(),
            ScanResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scan().limit(2).invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,sss", result.getExclusiveStartKey());
    }

    @Test
    public void testScan_shouldCutResponseShort_whenLimitIsExceeded() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .segment(0)
                .totalSegments(1)
                .limit(1)
                .build(),
            ScanResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scan().limit(1).invoke();

        // Verify
        Assertions.assertEquals(List.of(new MockItem("p", "s", 123)), result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s", result.getExclusiveStartKey());
    }

    @Test
    public void testScanAsync_shouldReturnNothing_whenEmptyResponse() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .segment(0)
                .totalSegments(1)
                .build(),
            ScanResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scanAsync().invoke();

        // Verify
        Assertions.assertEquals(List.of(), result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testScanAsync_shouldReturnItemsFromAllPages_whenResultIsMultipage() {
        // Setup
        var client = new MockClient(
            List.of(
                ScanRequest.builder()
                    .tableName("mock")
                    .segment(0)
                    .totalSegments(1)
                    .build(),
                ScanRequest.builder()
                    .tableName("mock")
                    .segment(0)
                    .totalSegments(1)
                    .exclusiveStartKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build()),
            List.of(
                ScanResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                        Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                    .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("s500")))
                    .build(),
                ScanResponse.builder()
                    .items(List.of(
                        Map.of("partition", av("p"), "sort", av("sssss"), "ival", av(789))))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scanAsync().invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456),
            new MockItem("p", "sssss", 789)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertNull(result.getExclusiveStartKey());
    }

    @Test
    public void testScanAsync_shouldReturnExclusiveStart_whenLimitIsReached() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .segment(0)
                .totalSegments(1)
                .limit(2)
                .build(),
            ScanResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scanAsync().limit(2).invoke();

        // Verify
        Assertions.assertEquals(List.of(
            new MockItem("p", "s", 123),
            new MockItem("p", "sss", 456)),
            result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,sss", result.getExclusiveStartKey());
    }

    @Test
    public void testScanAsync_shouldCutResponseShort_whenLimitIsExceeded() {
        // Setup
        var client = new MockClient(
            ScanRequest.builder()
                .tableName("mock")
                .limit(1)
                .segment(0)
                .totalSegments(1)
                .build(),
            ScanResponse.builder()
                .items(List.of(
                    Map.of("partition", av("p"), "sort", av("s"), "ival", av(123)),
                    Map.of("partition", av("p"), "sort", av("sss"), "ival", av(456))))
                .lastEvaluatedKey(Map.of("partition", av("p"), "sort", av("sss")))
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.scanAsync().limit(1).invoke();

        // Verify
        Assertions.assertEquals(List.of(new MockItem("p", "s", 123)), result.stream().collect(Collectors.toList()));
        Assertions.assertEquals("p,s", result.getExclusiveStartKey());
    }

    private static AttributeValue av(String value) {
        return DynamoTableTest.av(value);
    }

    private static AttributeValue av(int value) {
        return DynamoTableTest.av(value);
    }
}
