package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
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
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DynamoTableTest {

    @Test
    public void testConstructor_shouldThrow_whenBothClientsNull() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new MockTable(null, null, "mock"));
    }

    @Test
    public void testConstructor_shouldThrow_whenTableNameNull() {
        Assertions.assertThrows(NullPointerException.class,
            () -> new MockTable(new MockClient(), null, null));
    }

    @Test
    public void testGetItem_shouldBuildQueryAndParseResponse_whenItemInResponse() {
        // Setup
        var client = new MockClient(getItemReq().build(), getItemResp().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItem("p", "s");

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 10), result);
    }

    @Test
    public void testGetItem_returnNull_whenNoItemInResponse() {
        // Setup
        var client = new MockClient(getItemReq().build(), getItemResp().item(null).build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItem("p", "s");

        // Verify
        Assertions.assertNull(result);
    }

    @Test
    public void testGetItemExtended_shouldReturnCapacity_whenProvided() {
        // Setup
        var client = new MockClient(getItemReq().consistentRead(true).returnConsumedCapacity(ReturnConsumedCapacity.TOTAL).build(),
            getItemResp().consumedCapacity(ConsumedCapacity.builder().capacityUnits(2.0).build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItemExtended("p", "s", true);

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 10), result.getItem());
        Assertions.assertEquals(2.0, result.getCapacity());
    }

    @Test
    public void testGetItemAsync_shouldBuildQueryAndParseResponse_whenItemInResponse() {
        // Setup
        var client = new MockClient(getItemReq().build(), getItemResp().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItemAsync("p", "s");

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 10), result.join());
    }

    @Test
    public void testGetItemAsync_returnNull_whenNoItemInResponse() {
        // Setup
        var client = new MockClient(getItemReq().build(), getItemResp().item(null).build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItemAsync("p", "s");

        // Verify
        Assertions.assertNull(result.join());
    }

    @Test
    public void testGetItemExtendedAsync_shouldReturnCapacity_whenProvided() {
        // Setup
        var client = new MockClient(getItemReq().consistentRead(true).returnConsumedCapacity(ReturnConsumedCapacity.TOTAL).build(),
            getItemResp().consumedCapacity(ConsumedCapacity.builder().capacityUnits(2.0).build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getItemExtendedAsync("p", "s", true).join();

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 10), result.getItem());
        Assertions.assertEquals(2.0, result.getCapacity());
    }

    @Test
    public void testGetBatchByItem_shouldBreakUpReq_whenMoreThan25Items() {
        // Setup
        var keys = new ArrayList<MockItem>();
        var expectedResult = new ArrayList<MockItem>();
        var reqItems = new ArrayList<Map<String, AttributeValue>>();
        var respItems = new ArrayList<Map<String, AttributeValue>>();
        for (var i = 0; i < 55; ++i) {
            keys.add(new MockItem("p" + i, "s" + i, -1));
            expectedResult.add(new MockItem("p" + i, "s" + i, i));
            reqItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i)));
            respItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i)));
        }
        var client = new MockClient(
            List.of(
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(0, 25)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(25, 50)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(50, 55)).consistentRead(false).build())).build()),
            List.of(
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(0, 25))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(25, 50))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(50, 55))).build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getBatchByItem(keys);

        // Verify
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testGetBatchByKey_shouldBreakUpReq_whenMoreThan25Items() {
        // Setup
        var keys = new ArrayList<Key<String, Void, Void, Void, String>>();
        var expectedResult = new ArrayList<MockItem>();
        var reqItems = new ArrayList<Map<String, AttributeValue>>();
        var respItems = new ArrayList<Map<String, AttributeValue>>();
        for (var i = 0; i < 55; ++i) {
            keys.add(Key.of("p" + i, "s" + i));
            expectedResult.add(new MockItem("p" + i, "s" + i, i));
            reqItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i)));
            respItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i)));
        }
        var client = new MockClient(
            List.of(
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(0, 25)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(25, 50)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(50, 55)).consistentRead(false).build())).build()),
            List.of(
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(0, 25))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(25, 50))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(50, 55))).build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getBatchByKey(keys);

        // Verify
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testGetBatchByItemExtended_shouldTotalCapacity_whenMoreThan25Items() {
        // Setup
        var keys = new ArrayList<MockItem>();
        var expectedResult = new ArrayList<MockItem>();
        var reqItems = new ArrayList<Map<String, AttributeValue>>();
        var respItems = new ArrayList<Map<String, AttributeValue>>();
        for (var i = 0; i < 50; ++i) {
            keys.add(new MockItem("p" + i, "s" + i, -1));
            expectedResult.add(new MockItem("p" + i, "s" + i, i));
            reqItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i)));
            respItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i)));
        }
        var client = new MockClient(
            List.of(
                BatchGetItemRequest.builder()
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(0, 25)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder()
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(25, 50)).consistentRead(false).build())).build()),
            List.of(
                BatchGetItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedKeys(Map.of("mock", KeysAndAttributes.builder()
                                                        .keys(List.of(Map.of("partition", av("px"),
                                                            "sort", av("sx")))).build()))
                    .responses(Map.of("mock", respItems.subList(0, 25))).build(),
                BatchGetItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedKeys(Map.of("mock", KeysAndAttributes.builder()
                                                        .keys(List.of(Map.of("partition", av("py"),
                                                            "sort", av("sy")))).build()))
                    .responses(Map.of("mock", respItems.subList(25, 50))).build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getBatchByItemExtended(keys, false);

        // Verify
        Assertions.assertEquals(expectedResult, result.getItems());
        Assertions.assertEquals(50.0, result.getCapacity());
        Assertions.assertEquals(List.of(Key.of("px", "sx"), Key.of("py", "sy")), result.getUnprocessedValues());
    }

    @Test
    public void testGetBatchByItemAsync_shouldBreakUpReq_whenMoreThan25Items() {
        // Setup
        var keys = new ArrayList<MockItem>();
        var expectedResult = new ArrayList<MockItem>();
        var reqItems = new ArrayList<Map<String, AttributeValue>>();
        var respItems = new ArrayList<Map<String, AttributeValue>>();
        for (var i = 0; i < 55; ++i) {
            keys.add(new MockItem("p" + i, "s" + i, -1));
            expectedResult.add(new MockItem("p" + i, "s" + i, i));
            reqItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i)));
            respItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i)));
        }
        var client = new MockClient(
            List.of(
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(0, 25)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(25, 50)).consistentRead(false).build())).build(),
                BatchGetItemRequest.builder().requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(50, 55)).consistentRead(false).build())).build()),
            List.of(
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(0, 25))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(25, 50))).build(),
                BatchGetItemResponse.builder().responses(Map.of("mock", respItems.subList(50, 55))).build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getBatchByItemAsync(keys);

        // Verify
        Assertions.assertEquals(expectedResult, result.join());
    }

    @Test
    public void testGetBatchByItemExtendedAsync_shouldTotalCapacity_whenMoreThan25Items() {
        // Setup
        var keys = new ArrayList<MockItem>();
        var expectedResult = new ArrayList<MockItem>();
        var reqItems = new ArrayList<Map<String, AttributeValue>>();
        var respItems = new ArrayList<Map<String, AttributeValue>>();
        for (var i = 0; i < 50; ++i) {
            keys.add(new MockItem("p" + i, "s" + i, -1));
            expectedResult.add(new MockItem("p" + i, "s" + i, i));
            reqItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i)));
            respItems.add(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i)));
        }
        var client = new MockClient(
            List.of(
                BatchGetItemRequest.builder()
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(0, 25)).consistentRead(false)
                            .build())).build(),
                BatchGetItemRequest.builder()
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .requestItems(Map.of("mock", KeysAndAttributes.builder().keys(reqItems.subList(25, 50)).consistentRead(false)
                            .build())).build()),
            List.of(
                BatchGetItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedKeys(Map.of("mock", KeysAndAttributes.builder()
                                                        .keys(List.of(Map.of("partition", av("px"),
                                                            "sort", av("sx")))).build()))
                    .responses(Map.of("mock", respItems.subList(0, 25))).build(),
                BatchGetItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedKeys(Map.of("mock", KeysAndAttributes.builder()
                                                        .keys(List.of(Map.of("partition", av("py"),
                                                            "sort", av("sy")))).build()))
                    .responses(Map.of("mock", respItems.subList(25, 50))).build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.getBatchByItemExtendedAsync(keys, false).join();

        // Verify
        Assertions.assertEquals(expectedResult, result.getItems());
        Assertions.assertEquals(50.0, result.getCapacity());
        Assertions.assertEquals(List.of(Key.of("px", "sx"), Key.of("py", "sy")), result.getUnprocessedValues());
    }

    @Test
    public void testPutItem_shouldSubmitRequestToDynamo_whenCalled() {
        // Setup
        var client = new MockClient(PutItemRequest.builder()
                                        .tableName("mock")
                                        .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10),
                                            "stringSet", AttributeValue.fromSs(List.of()))).build(),
            null);
        var table = new MockTable(client, null, "mock");

        // Act
        table.putItem(new MockItem("p", "s", 10));

        // No verify step, was verified by the MockClient
    }

    @Test
    public void testUpdateItem_shouldSubmitRequestToDynamo_whenCalled() {
        // Setup
        var client = new MockClient(UpdateItemRequest.builder()
                .tableName("mock").key(
                        Map.of("partition", AttributeValue.builder().s("p").build(),
                                "sort", AttributeValue.builder().s("s").build())
                )
                .returnValues(ReturnValue.ALL_NEW)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .updateExpression("SET #stringSet = :stringSet, #ival = :ival")
                .expressionAttributeNames(Map.of("#stringSet", "stringSet", "#ival", "ival"))
                .expressionAttributeValues(Map.of(":ival", AttributeValue.fromN("123"),
                    ":stringSet", AttributeValue.fromSs(List.of())))
                .build(),
                UpdateItemResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        table.updateItem(new MockItem("p", "s", 123), null,
                null, null,
                null, false);

        // No verify step, was verified by the MockClient
    }

    @Test
    public void testUpdateItem_shouldNotDefineKey_whenIgnoreAttributePresent() {
        // Setup
        var client = new MockClient(UpdateItemRequest.builder()
                .tableName("mock").key(
                        Map.of("partition", AttributeValue.builder().s("p").build(),
                        "sort", AttributeValue.builder().s("s").build())
                )
                .returnValues(ReturnValue.ALL_NEW)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .updateExpression("SET #stringSet = :stringSet")
                .expressionAttributeNames(Map.of("#stringSet", "stringSet"))
                .expressionAttributeValues(Map.of(":stringSet", AttributeValue.fromSs(List.of())))
                .build(),
                UpdateItemResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        table.updateItem(new MockItem("p", "s", 123), null,
                Map.of("ival", DynamoTable.UPDATE_IGNORE_ATTRIBUTE), null,
                null, false);

        // No verify step, was verified by the MockClient
    }

    @Test
    public void testUpdateItem_shouldNotIncludeAttributeValues_whenNoneGiven() {
        // Setup
        var client = new MockClient(UpdateItemRequest
                .builder()
                .tableName("mock")
                .key(Map.of("partition", AttributeValue.builder().s("p").build(),
                        "sort", AttributeValue.builder().s("s").build()))
                .returnValues(ReturnValue.ALL_NEW)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .updateExpression(" REMOVE #ival")
                .expressionAttributeNames(Map.of("#ival", "ival"))
                .build(),
                UpdateItemResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        table.updateItem("p", "s", null,
                Map.of("ival", DynamoTable.UPDATE_REMOVE_ATTRIBUTE), null,
                null, false);

        // No verify step, was verified by the MockClient
    }

    @Test
    public void testUpdateItem_shouldAddItemsToStringSets_whenAddSyntaxUsed() {
        // Setup
        var client = new MockClient(UpdateItemRequest
            .builder()
            .tableName("mock")
            .key(Map.of("partition", AttributeValue.fromS("p"),
                "sort", AttributeValue.fromS("s")))
            .returnValues(ReturnValue.ALL_NEW)
            .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
            .updateExpression(" ADD #stringSet :newStringSet")
            .expressionAttributeNames(Map.of("#stringSet", "stringSet"))
            .expressionAttributeValues(Map.of(":newStringSet", AttributeValue.fromSs(List.of("Hello bro"))))
            .build(),
            UpdateItemResponse.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        table.updateItem("p", "s", null,
            Map.of("stringSet", "+:newStringSet"),
            Map.of(":newStringSet", AttributeValue.fromSs(List.of("Hello bro"))),
            null, false);

        // No verify step, was verified by the MockClient
    }

    @Test
    public void testUpdateItem_shouldRemoveAttribute_whenRemovingFieldThatIsPopulatedByItem() {
        // Setup
        var client = new MockClient(UpdateItemRequest
                .builder()
                .tableName("mock")
                .key(Map.of("partition", AttributeValue.builder().s("p").build(),
                        "sort", AttributeValue.builder().s("s").build()))
                .returnValues(ReturnValue.ALL_NEW)
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .updateExpression("SET #stringSet = :stringSet REMOVE #ival")
                // There should NOT be an attribute value map defining ival, just the attribute name map
                .expressionAttributeNames(Map.of("#ival", "ival", "#stringSet", "stringSet"))
                .expressionAttributeValues(Map.of(":stringSet", AttributeValue.fromSs(List.of())))
                .build(),
                UpdateItemResponse.builder().build());
        var table = new MockTable(client, null, "mock");
        // The item we are passing in has a non-null value for the field we are removing, "ival".
        var item = new MockItem("p", "s", 123);

        // Act
        table.updateItem(item, null,
                Map.of("ival", DynamoTable.UPDATE_REMOVE_ATTRIBUTE), null,
                null, false);

        // No verify step, was verified by the MockClient
    }


    @Test
    public void testPutItemExtended_shouldReturnPreviousItemAndCapacity_whenAsked() {
        // Setup
        var client = new MockClient(
            PutItemRequest.builder()
                .tableName("mock")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10),
                    "stringSet", AttributeValue.fromSs(List.of())))
                .build(),
            PutItemResponse.builder()
                .attributes(Map.of("partition", av("p"), "sort", av("s"), "ival", av(5),
                    "stringSet", AttributeValue.fromSs(List.of())))
                .consumedCapacity(ConsumedCapacity.builder()
                                      .capacityUnits(3.0)
                                      .build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var response = table.putItemExtended(new MockItem("p", "s", 10), null, true);

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 5), response.getItem());
        Assertions.assertEquals(3.0, response.getCapacity());
    }

    @Test
    public void testPutItemExtended_shouldThrow_whenKeyConditionFails() {
        // Setup
        var client = new MockClient(
            PutItemRequest.builder()
                .tableName("mock")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10), "stringSet", AttributeValue.fromSs(List.of())))
                .conditionExpression("attribute_not_exists(#partition)")
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .build(),
            ConditionalCheckFailedException.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act and verify
        Assertions.assertThrows(ConditionalCheckFailedException.class,
            () -> table.putItemExtended(new MockItem("p", "s", 10),
                ConditionExpression.requireAbsent(table),
                true));
    }

    @Test
    public void testPutBatch_shouldBreakUpList_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 49; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().putRequest(
                PutRequest.builder()
                    .item(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i),
                        "stringSet", AttributeValue.fromSs(List.of()))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                        .build(),
                BatchWriteItemRequest.builder()
                .requestItems(Map.of("mock", maps.subList(25, 49)))
                .build()),
            List.of(BatchWriteItemResponse.builder().build(),
                BatchWriteItemResponse.builder().build()));
        var table = new MockTable(client, null, "mock");

        // Act
        table.putBatch(items);

        // Verify
        client.assertDone();
    }

    @Test
    public void testPutBatchExtended_shouldBreakUpListAndReturnCapacity_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 49; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().putRequest(
                PutRequest.builder()
                    .item(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i),
                        "stringSet", AttributeValue.fromSs(List.of()))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 49)))
                    .build()),
            List.of(
                BatchWriteItemResponse.builder().consumedCapacity(
                    ConsumedCapacity.builder().capacityUnits(25.0).build()).build(),
                BatchWriteItemResponse.builder().consumedCapacity(
                    ConsumedCapacity.builder().capacityUnits(24.0).build())
                    .unprocessedItems(Map.of("mock",
                        List.of(WriteRequest.builder()
                                    .putRequest(
                                        PutRequest.builder()
                                            .item(Map.of("partition", av("xyz"), "sort", av("abc"), "ival", av(100),
                                                "stringSet", AttributeValue.fromSs(List.of())))
                                            .build())
                                    .build())))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.putBatchExtended(items);

        // Verify
        Assertions.assertEquals(49.0, result.getCapacity());
        Assertions.assertEquals(List.of(new MockItem("xyz", "abc", 100)), result.getUnprocessedValues());
    }

    @Test
    public void testPutItemAsync_shouldSubmitRequestToDynamo_whenCalled() {
        // Setup
        var client = new MockClient(PutItemRequest.builder()
                                        .tableName("mock")
                                        .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10),
                                            "stringSet", AttributeValue.fromSs(List.of()))).build(),
            null);
        var table = new MockTable(client, null, "mock");

        // Act
        table.putItemAsync(new MockItem("p", "s", 10)).join();

        // Verify
        client.assertDone();
    }

    @Test
    public void testPutItemExtendedAsync_shouldReturnPreviousItemAndCapacity_whenAsked() {
        // Setup
        var client = new MockClient(
            PutItemRequest.builder()
                .tableName("mock")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10),
                    "stringSet", AttributeValue.fromSs(List.of())))
                .build(),
            PutItemResponse.builder()
                .attributes(Map.of("partition", av("p"), "sort", av("s"), "ival", av(5),
                    "stringSet", AttributeValue.fromSs(List.of())))
                .consumedCapacity(ConsumedCapacity.builder().capacityUnits(3.0).build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var response = table.putItemExtendedAsync(new MockItem("p", "s", 10), null, true).join();

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 5), response.getItem());
        Assertions.assertEquals(3.0, response.getCapacity());
    }

    @Test
    public void testPutItemExtendedAsync_shouldThrow_whenKeyConditionFails() throws Exception {
        // Setup
        var client = new MockClient(
            PutItemRequest.builder()
                .tableName("mock")
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10),
                    "stringSet", AttributeValue.fromSs(List.of())))
                .conditionExpression("attribute_not_exists(#partition)")
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .expressionAttributeNames(Map.of("#partition", "partition"))
                .build(),
            ConditionalCheckFailedException.builder().build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.putItemExtendedAsync(new MockItem("p", "s", 10),
                ConditionExpression.requireAbsent(table),
                true);

        // Verify
        try {
            result.get();
            throw new RuntimeException("Should have failed in the get");
        } catch (ExecutionException e) {
            client.assertDone();
            Assertions.assertEquals(ConditionalCheckFailedException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testPutBatchAsync_shouldBreakUpList_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 49; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().putRequest(
                PutRequest.builder()
                    .item(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i),
                        "stringSet", AttributeValue.fromSs(List.of()))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 49)))
                    .build()),
            List.of(BatchWriteItemResponse.builder().build(),
                BatchWriteItemResponse.builder().build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        table.putBatchAsync(items).join();

        // Verify
        client.assertDone();
    }

    @Test
    public void testPutBatchExtendedAsync_shouldBreakUpListAndReturnCapacity_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 49; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().putRequest(
                PutRequest.builder()
                    .item(Map.of("partition", av("p" + i), "sort", av("s" + i), "ival", av(i),
                        "stringSet", AttributeValue.fromSs(List.of()))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 49)))
                    .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                    .build()),
            List.of(
                BatchWriteItemResponse.builder().consumedCapacity(
                    ConsumedCapacity.builder().capacityUnits(25.0).build()).build(),
                BatchWriteItemResponse.builder().consumedCapacity(
                    ConsumedCapacity.builder().capacityUnits(24.0).build())
                    .unprocessedItems(Map.of("mock",
                        List.of(WriteRequest.builder()
                                    .putRequest(
                                        PutRequest.builder()
                                            .item(Map.of("partition", av("xyz"), "sort", av("abc"), "ival", av(100)))
                                            .build())
                                    .build())))
                    .build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.putBatchExtendedAsync(items).join();

        // Verify
        Assertions.assertEquals(49.0, result.getCapacity());
        Assertions.assertEquals(List.of(new MockItem("xyz", "abc", 100)), result.getUnprocessedValues());
    }

    @Test
    public void testDeleteItem_shouldSubmitRequest_whenCalled() {
        // Setup
        var client = new MockClient(
            DeleteItemRequest.builder().tableName("mock").key(Map.of("partition", av("p"), "sort", av("s"))).build(),
            null);
        var table = new MockTable(client, null, "mock");

        // Act
        table.deleteItem(new MockItem("p", "s", 100));

        // Verify
        client.assertDone();
    }

    @Test
    public void testDeleteItemExtended_shouldReturnItem_whenCalled() {
        // Setup
        var client = new MockClient(
            DeleteItemRequest.builder()
                .tableName("mock")
                .key(Map.of("partition", av("p"), "sort", av("s")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .build(),
            DeleteItemResponse.builder()
                .attributes(Map.of("partition", av("p"), "sort", av("s"), "ival", av(15)))
                .consumedCapacity(ConsumedCapacity.builder().capacityUnits(1.0).build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.deleteItemExtended(new MockItem("p", "s", 100), null, true);

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 15), result.getItem());
        Assertions.assertEquals(1.0, result.getCapacity());
    }

    @Test
    public void testDeleteBatchByItem_shouldBreakUpBatch_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 48; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().deleteRequest(
                DeleteRequest.builder()
                    .key(Map.of("partition", av("p" + i), "sort", av("s" + i))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 48)))
                    .build()),
            List.of(BatchWriteItemResponse.builder().build(),
                BatchWriteItemResponse.builder().build()));
        var table = new MockTable(client, null, "mock");

        // Act
        table.deleteBatchByItem(items);

        // Verify
        client.assertDone();
    }

    @Test
    public void testDeleteBatchByKey_shouldBreakUpBatch_whenMoreThan25() {
        // Setup
        var keys = new ArrayList<Key<String, Void, Void, Void, String>>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 48; ++i) {
            keys.add(Key.of("p" + i, "s" + i));
            maps.add(WriteRequest.builder().deleteRequest(
                DeleteRequest.builder()
                    .key(Map.of("partition", av("p" + i), "sort", av("s" + i))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 48)))
                    .build()),
            List.of(BatchWriteItemResponse.builder().build(),
                BatchWriteItemResponse.builder().build()));
        var table = new MockTable(client, null, "mock");

        // Act
        table.deleteBatchByKey(keys);

        // Verify
        client.assertDone();
    }

    @Test
    public void testDeleteBatchExtended_shouldBreakUpBatch_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 48; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().deleteRequest(
                DeleteRequest.builder()
                    .key(Map.of("partition", av("p" + i), "sort", av("s" + i))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 48)))
                    .build()),
            List.of(
                BatchWriteItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedItems(Map.of("mock", List.of(
                        WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                               .key(Map.of("partition", av("aaa"), "sort", av("bbb")))
                                               .build())
                            .build())))
                    .build(),
                BatchWriteItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(23.0).build())
                    .unprocessedItems(Map.of("mock", List.of(
                        WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                               .key(Map.of("partition", av("ccc"), "sort", av("ddd")))
                                               .build())
                            .build())))
                    .build()));
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.deleteBatchByItemExtended(items);

        // Verify
        Assertions.assertEquals(48.0, result.getCapacity());
        Assertions.assertEquals(List.of(Key.of("aaa", "bbb"), Key.of("ccc", "ddd")),
            result.getUnprocessedValues());
    }

    @Test
    public void testDeleteItemAsync_shouldSubmitRequest_whenCalled() {
        // Setup
        var client = new MockClient(
            DeleteItemRequest.builder().tableName("mock").key(Map.of("partition", av("p"), "sort", av("s"))).build(),
            null);
        var table = new MockTable(client, null, "mock");

        // Act
        table.deleteItemAsync(new MockItem("p", "s", 100)).join();

        // Verify
        client.assertDone();
    }

    @Test
    public void testDeleteItemExtendedAsync_shouldReturnItem_whenCalled() {
        // Setup
        var client = new MockClient(
            DeleteItemRequest.builder()
                .tableName("mock")
                .key(Map.of("partition", av("p"), "sort", av("s")))
                .returnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
                .returnValues(ReturnValue.ALL_OLD)
                .build(),
            DeleteItemResponse.builder()
                .attributes(Map.of("partition", av("p"), "sort", av("s"), "ival", av(15)))
                .consumedCapacity(ConsumedCapacity.builder().capacityUnits(1.0).build())
                .build());
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.deleteItemExtendedAsync(new MockItem("p", "s", 100), true).join();

        // Verify
        Assertions.assertEquals(new MockItem("p", "s", 15), result.getItem());
        Assertions.assertEquals(1.0, result.getCapacity());
    }

    @Test
    public void testDeleteBatchAsync_shouldBreakUpBatch_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 48; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().deleteRequest(
                DeleteRequest.builder()
                    .key(Map.of("partition", av("p" + i), "sort", av("s" + i))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 48)))
                    .build()),
            List.of(BatchWriteItemResponse.builder().build(),
                BatchWriteItemResponse.builder().build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        table.deleteBatchByItemAsync(items).join();

        // Verify
        client.assertDone();
    }

    @Test
    public void testDeleteBatchExtendedAsync_shouldBreakUpBatch_whenMoreThan25() {
        // Setup
        var items = new ArrayList<MockItem>();
        var maps = new ArrayList<WriteRequest>();
        for (var i = 0; i < 48; ++i) {
            items.add(new MockItem("p" + i, "s" + i, i));
            maps.add(WriteRequest.builder().deleteRequest(
                DeleteRequest.builder()
                    .key(Map.of("partition", av("p" + i), "sort", av("s" + i))).build())
                         .build());
        }
        var client = new MockClient(
            List.of(
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(0, 25)))
                    .build(),
                BatchWriteItemRequest.builder()
                    .requestItems(Map.of("mock", maps.subList(25, 48)))
                    .build()),
            List.of(
                BatchWriteItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(25.0).build())
                    .unprocessedItems(Map.of("mock", List.of(
                        WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                               .key(Map.of("partition", av("aaa"), "sort", av("bbb")))
                                               .build())
                            .build())))
                    .build(),
                BatchWriteItemResponse.builder()
                    .consumedCapacity(ConsumedCapacity.builder().capacityUnits(23.0).build())
                    .unprocessedItems(Map.of("mock", List.of(
                        WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                               .key(Map.of("partition", av("ccc"), "sort", av("ddd")))
                                               .build())
                            .build())))
                    .build()), true);
        var table = new MockTable(client, null, "mock");

        // Act
        var result = table.deleteBatchByItemExtendedAsync(items).join();

        // Verify
        Assertions.assertEquals(48.0, result.getCapacity());
        Assertions.assertEquals(List.of(Key.of("aaa", "bbb"), Key.of("ccc", "ddd")),
            result.getUnprocessedValues());
    }

    protected GetItemRequest.Builder getItemReq() {
        return GetItemRequest.builder()
                   .tableName("mock")
                   .key(Map.of("partition", av("p"), "sort", av("s")));
    }

    protected GetItemResponse.Builder getItemResp() {
        return GetItemResponse.builder()
                   .item(Map.of("partition", av("p"), "sort", av("s"), "ival", av(10)));
    }

    public static AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }

    public static AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }
}
