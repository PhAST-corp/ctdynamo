package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.NoIndex;
import ai.phast.ctdynamo.tables.NoIndexDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DynamoMockUtilTest {

    @Test
    public void testVerifyContainsExactly_shouldreportErrors_whenNoMatch() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("farm", 10, true),
            new NoIndex("farm", 20, false),
            new NoIndex("house", 10, true));

        // Act & Test
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("farm", 10, true),
            new NoIndex("farm", 20, false),
            new NoIndex("house", 10, true));
        Assertions.assertThrows(AssertionError.class, () -> DynamoMockUtil.verifyContainsExactly(table));
        Assertions.assertThrows(AssertionError.class, () -> DynamoMockUtil.verifyContainsExactly(table,
            new NoIndex("farm", 10, true),
            new NoIndex("farm", 20, false),
            new NoIndex("house", 10, true),
            new NoIndex("park", 15, true)));
        Assertions.assertThrows(AssertionError.class, () -> DynamoMockUtil.verifyContainsExactly(table,
            new NoIndex("farm", 10, true),
            new NoIndex("farm", 20, false),
            new NoIndex("house", 10, false)));
    }

}
