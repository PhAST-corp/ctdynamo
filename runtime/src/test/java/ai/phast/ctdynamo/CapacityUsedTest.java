package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class CapacityUsedTest {

    @Test
    public void testAdd_ShouldAddIndexes_whenNotInOriginal() {
        //Setup
        var c1 = new CapacityUsed(1,2,0,4, Map.of());
        var c2 = new CapacityUsed(3,5,3,0,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2)));

        //Act
        c1.add(c2);

        //Verify
        Assertions.assertEquals(new CapacityUsed(4,7,3,4,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2))), c1);
    }

    @Test
    public void testAdd_ShouldCombineIndexes_whenInBoth() {
        //Setup
        var c1 = new CapacityUsed(1,2,17,4.5,
                Map.of("Test", new CapacityUsed.ReadWrite(1.5, 6),
                        "TestThis", new CapacityUsed.ReadWrite(0, 9)));
        var c2 = new CapacityUsed(3,5,3,0,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2),
                        "TestOther", new CapacityUsed.ReadWrite(2, 5)));

        //Act
        c1.add(c2);

        //Verify
        Assertions.assertEquals(new CapacityUsed(4,7,20,4.5,
                Map.of("Test", new CapacityUsed.ReadWrite(2.5, 8),
                        "TestThis", new CapacityUsed.ReadWrite(0, 9),
                        "TestOther", new CapacityUsed.ReadWrite(2, 5))), c1);
    }

    @Test
    public void testAdd_ShouldDoNothing_whenGivenNull() {
        //Setup
        var c1 = new CapacityUsed(3,5,3,0,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2)));

        //Act
        c1.add((CapacityUsed) null);

        //Verify
        Assertions.assertEquals(new CapacityUsed(3,5,3,0,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2))), c1);
    }

    @Test
    public void testAdd_ShouldAddValues_whenGivenNullIndexes() {
        //Setup
        var c1 = new CapacityUsed(1,2,0,4,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2)));
        var c2 = new CapacityUsed(3,5,3,0,
                null);

        //Act
        c1.add(c2);

        //Verify
        Assertions.assertEquals(new CapacityUsed(4,7,3,4,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2))), c1);
    }

    @Test
    public void testAdd_ShouldAddValues_whenHaveNullIndexes() {
        //Setup
        var c1 = new CapacityUsed(1,2,0,4, null);
        var c2 = new CapacityUsed(3,5,3,0,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2)));

        //Act
        c1.add(c2);

        //Verify
        Assertions.assertEquals(new CapacityUsed(4,7,3,4,
                Map.of("Test", new CapacityUsed.ReadWrite(1, 2))), c1);
    }
}
