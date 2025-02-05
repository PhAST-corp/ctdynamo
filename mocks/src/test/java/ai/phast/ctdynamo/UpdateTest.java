package ai.phast.ctdynamo;

import ai.phast.ctdynamo.processorTest.ArrayOuterItem;
import ai.phast.ctdynamo.processorTest.ArrayOuterItemDynamoTable;
import ai.phast.ctdynamo.tables.NoIndex;
import ai.phast.ctdynamo.tables.NoIndexDynamoTable;
import ai.phast.ctdynamo.tables.NoSortKey;
import ai.phast.ctdynamo.tables.NoSortKeyDynamoTable;
import ai.phast.ctdynamo.tables.WithIndex;
import ai.phast.ctdynamo.tables.WithIndexDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdateTest {

    @Test
    public void testUpdate_shouldAddItem_whenNotPresent() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class);

        // Act
        var result = table.updateItem(new NoIndex("x", 5, true), null, null, null, null, true);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("x", 5, true));
        Assertions.assertNull(result.getItem());
    }

    @Test
    public void testUpdate_shouldChangeItem_whenItemExists() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class,
            new NoSortKey("x", 1, 2, null));

        // Act
        var result1 = table.updateItem("x", null, null,
            Map.of("i1p", "if_not_exists(#i1p, :zero) + :ten"),
            Map.of(":zero", av(0), ":ten", av(10)),
            Map.of("#i1p", "i1p"), true);
        var result2 = table.updateItem("y", null, null,
            Map.of("i1p", "if_not_exists(#i1p, :zero) + :ten"),
            Map.of(":zero", av(0), ":ten", av(10)),
            Map.of("#i1p", "i1p"), true);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoSortKey("x", 11, 2, null), new NoSortKey("y", 10, 0, null));
        Assertions.assertEquals(new NoSortKey("x", 1, 2, null), result1.getItem());
        Assertions.assertNull(result2.getItem());
    }

    @Test
    public void testRemove_shouldRemoveAttribute_whenRequested() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class,
            new NoSortKey("x", 1, 2, Instant.ofEpochMilli(100000)));

        // Act
        var result = table.updateItem("x", null, null, Map.of("when", DynamoTable.UPDATE_REMOVE_ATTRIBUTE),
            null, null, false);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoSortKey("x", 1, 2, null));
        Assertions.assertEquals(new NoSortKey("x", 1, 2, null), result.getItem());
    }

    @Test
    public void testUpdate_shouldHandleMultipleUpdateTypes_whenRequested() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class,
            new NoSortKey("x", 1, 2, Instant.ofEpochMilli(100000)));

        // Act
        var result = table.updateItem("x", null, null,
            Map.of("when", DynamoTable.UPDATE_REMOVE_ATTRIBUTE, "i1p", ":i1p"),
            Map.of(":i1p", av(3)),null, false);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoSortKey("x", 3, 2, null));
        Assertions.assertEquals(new NoSortKey("x", 3, 2, null), result.getItem());
    }

    @Test
    public void testUpdate_shouldThrowConditionCheckFailedException_whenItemDoesNotExistAndConditionExpressionGiven() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class);

        // Act & Verify
        Assertions.assertThrows(ConditionalCheckFailedException.class,
            () -> table.updateItem("x", null, new ConditionExpression(
            "#ip1 = :ip1 AND contains(:validIP2, #ip2)",
                    Map.of(":ip1", av(1), ":validIP2", av(List.of("1", "2"))),
                    Map.of("#ip1", "ip1", "#ip2", "ip2")),
                Map.of("ip1", ":ip1"), Map.of(":ip1", av(1)), null, false));
    }

    @Test
    public void testListAppend_shouldAppend_whenExpressionProvided() {
        // Setup
        var prevItem = new ArrayOuterItem();
        prevItem.setNums(new int[]{1, 2, 3});
        prevItem.setPartition(10);
        var table = DynamoMockUtil.buildMockTable(ArrayOuterItemDynamoTable.class, prevItem);

        // Act
        var result = table.updateItem(10, null, null,
            Map.of("nums", "list_append(#nums, :new_nums)"),
            Map.of(":new_nums", AttributeValue.builder().l(List.of(
                AttributeValue.builder().n("10").build(),
                AttributeValue.builder().n("11").build())).build()),
            Map.of("#nums", "nums"), true);

        // Verify
        Assertions.assertEquals(prevItem, result.getItem());
        var expectedItem = new ArrayOuterItem();
        expectedItem.setNums(new int[]{1, 2, 3, 10, 11});
        expectedItem.setPartition(10);
        DynamoMockUtil.verifyContainsExactly(table, expectedItem);
    }

    @Test
    public void testUpdate_shouldThrowConditionalCheckFailure_whenComparingNonExistentAttributeToExistingAttribute() {
        // Setup
        var item = new WithIndex();
        item.setP("Partition");
        item.setS("Sort");
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class, item);

        // Act & Verify
        // Existing attribute should be "greater than" null value, so this check should fail
        Assertions.assertThrows(ConditionalCheckFailedException.class,
                () -> table.updateItem("Partition", "Sort", new ConditionExpression(
                                "#ip1 >= :ip1",
                                Map.of(":ip1", av("blah")),
                                Map.of("#ip1", "ip1")),
                        Map.of("ip1", ":ip1"), Map.of(":ip1", av(1)), null, false));
    }

    private AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }

    private AttributeValue av(List<String> values) {
        return AttributeValue.builder().l(values.stream()
            .map(v -> AttributeValue.builder().n(v).build()).collect(Collectors.toList())).build();
    }
}
