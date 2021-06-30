package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.NoIndex;
import ai.phast.ctdynamo.tables.NoIndexDynamoTable;
import ai.phast.ctdynamo.tables.NoSortKey;
import ai.phast.ctdynamo.tables.NoSortKeyDynamoTable;
import ai.phast.ctdynamo.tables.WithIndex;
import ai.phast.ctdynamo.tables.WithIndexDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;

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

    private AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }
}
