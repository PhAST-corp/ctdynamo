package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.GsiNoSortKey;
import ai.phast.ctdynamo.tables.GsiNoSortKeyDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

public class GsiNoSortTest {

    private GsiNoSortKeyDynamoTable table;

    @BeforeEach
    public void setup() {
        table = DynamoMockUtil.buildMockTable(GsiNoSortKeyDynamoTable.class);
    }

    @Test
    public void testSortless_shouldDoAllOps_whenUsed() {
        // Setup
        var original = new GsiNoSortKey("Part", "Sort", "Index");
        var replacement = new GsiNoSortKey("Part", "Sort", "OtherIndex");

        // Act
        table.putItem(original);
        var getItem1Out = table.getItem("Part", "Sort");
        var queryOut = table.getIndexIndex().query("Index").invoke().stream().collect(Collectors.toList());
        table.putItem(replacement);
        var getItem2Out = table.getItem("Part", "Sort");
        var query2Out = table.getIndexIndex().query("Index").invoke().stream().collect(Collectors.toList());
        var query3Out = table.getIndexIndex().query("OtherIndex").invoke().stream().collect(Collectors.toList());
        table.deleteItem("Part", "Sort");
        var getItem3Out = table.getItem("Part", "Sort");
        var query4Out = table.getIndexIndex().query("OtherIndex").invoke().stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(original, getItem1Out);
        Assertions.assertEquals(List.of(original), queryOut);
        Assertions.assertEquals(replacement, getItem2Out);
        Assertions.assertEquals(List.of(), query2Out);
        Assertions.assertEquals(List.of(replacement), query3Out);
        Assertions.assertNull(getItem3Out);
        Assertions.assertEquals(List.of(), query4Out);
    }
}
