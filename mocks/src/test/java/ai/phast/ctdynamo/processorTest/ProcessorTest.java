package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.DynamoMockUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        var outer1 = new OuterItem("outer1", inner1, List.of(inner2, inner3),
            Map.of("five", 5, "ten", 10),
            Map.of(OuterItem.Flavor.BITTER, "beer"));

        // Outer 2: Everything but partition key null
        var outer2 = new OuterItem("outer2", null, null, null, null);

        // Outer 3: A list with some nulls
        var numberMap = new HashMap<String, Integer>();
        numberMap.put("zero", null);
        var flavorMap = new HashMap<OuterItem.Flavor, String>();
        flavorMap.put(OuterItem.Flavor.SOUR, null);
        flavorMap.put(OuterItem.Flavor.SALTY, "pretzels");
        var outer3 = new OuterItem("outer3", inner2, Arrays.asList(null, inner1), numberMap, flavorMap);

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

    @Test
    public void testArrayAsAttribute_shouldBeEncodedAndDecodedProperly_whenStoringVariousDatatypes() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(ArrayOuterItemDynamoTable.class);

        // Item 1: All values filled out, no nulls in arrays.
        var inner1 = new ArrayInnerItem(new double[]{3., 1., -5.45}, new Integer[]{1, -1, 0});

        // Item 2: All values filled out, nulls in arrays.
        var inner2 = new ArrayInnerItem(new double[]{0.01, 1.111, 3.56546456456}, new Integer[]{500, null, null});

        // Item 3: Empty arrays
        var inner3 = new ArrayInnerItem(new double[0], new Integer[0]);

        // Item 4: arrays that are not filled out
        var inner4 = new ArrayInnerItem(new double[3], new Integer[5]);

        var outer1 = new ArrayOuterItem(new int[]{1, 2, 3}, new ArrayInnerItem[]{inner1},
                new String[]{"string1", "string2"},
                new boolean[][]{
                        new boolean[]{true, false, false},
                        new boolean[]{true, false, true},
                        new boolean[]{false, false, false}
                },
                new short[][][]{
                        new short[][]{new short[]{1,2,3}, new short[]{1,2}},
                        new short[][]{new short[]{}, new short[]{1,25,6}},
                        new short[][]{new short[]{1,2,3}, new short[]{1,2}, new short[]{1,-3,5,7}}
                }, 1, "ind1", "sortInd1");

        var outer2 = new ArrayOuterItem(new int[]{}, new ArrayInnerItem[]{inner2, inner4, null},
                new String[]{"string1", null},
                new boolean[][]{
                        new boolean[]{true, true, false},
                        new boolean[]{false, true},
                        null
                },
                new short[][][]{
                new short[][]{new short[]{1,2,3}, null},
                new short[][]{},
                null
        },2, "ind2", "sortInd2");

        var outer3 = new ArrayOuterItem(null, new ArrayInnerItem[]{inner1, inner2, inner3, inner4},
                new String[]{},
                new boolean[][]{
                },
                new short[][][]{
                },3, "ind3", "sortInd3");

        var outer4 = new ArrayOuterItem(new int[]{2}, new ArrayInnerItem[]{null, null, null},
                new String[]{null, null},
                new boolean[][]{
                        null,
                        null,
                        null
                },
                new short[][][]{
                        null,
                        null,
                        null
                },4, "ind3", "sortInd3");

        // Act
        table.putBatch(List.of(outer1, outer2, outer3, outer4));

        var outer1Out = table.getItem(1, null);
        var outer2Out = table.getItem(2, null);
        var outer3Out = table.getItem(3, null);
        var outer4Out = table.getItem(4, null);

        // Verify
        Assertions.assertEquals(outer1, outer1Out);
        Assertions.assertEquals(outer2, outer2Out);
        Assertions.assertEquals(outer3, outer3Out);
        Assertions.assertEquals(outer4, outer4Out);
    }
}
