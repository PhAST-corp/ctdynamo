package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.DynamoMockUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ProcessorTest {

    @Test
    public void testOuterItem_shouldGetEqualObjectBack_whenStoringAndLoadingComplexObjects() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(OuterItemDynamoTable.class);

        // Item 1: All values filled out, nulls in lists.
        var inner1 = new InnerItem(InnerItem.Color.MAUVE, Arrays.asList(InnerItem.Color.RED, null, null, InnerItem.Color.BLUE),
            Arrays.asList(5, 10, null, 215),
            Arrays.asList(true, null),
            Set.of("this", "is", "a", "set"),
            List.of("this", "is", "a", "list"));
        // Item 2: All nulls
        var inner2 = new InnerItem();
        // Item 3: Mix of both
        var inner3 = new InnerItem(InnerItem.Color.GREEN, List.of(InnerItem.Color.GREEN), null, null, null, null);

        // Outer 1: Everything filled out
        var outer1 = new OuterItem("outer1", inner1, List.of(inner2, inner3));

        // Outer 2: Everything but partition key null
        var outer2 = new OuterItem("outer2", null, null);

        // Outer 3: A list with some nulls
        var outer3 = new OuterItem("outer3", inner2, Arrays.asList(null, inner1));

        // Act
        table.putBatch(List.of(outer1, outer2, outer3));
        var outer1Out = table.getItem("outer1", null);
        var outer2Out = table.getItem("outer2", null);
        var outer3Out = table.getItem("outer3", null);

        // Verify
        Assertions.assertEquals(outer1, outer1Out);
        Assertions.assertEquals(outer2, outer2Out);
        Assertions.assertEquals(outer3, outer3Out);
    }
}
