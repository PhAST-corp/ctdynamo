package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the Key factories. There is a single private constructor that only checks the first partition value, so
 * each factory is responsible for rejecting nulls among the partition values it was actually handed.
 */
public class KeyTest {

    @Test
    public void testOf_shouldLeaveUnusedPartitionsNull_whenGivenOne() {
        // Act
        var key = Key.of("p", "s");

        // Verify
        Assertions.assertEquals("p", key.getPartition1());
        Assertions.assertNull(key.getPartition2());
        Assertions.assertNull(key.getPartition3());
        Assertions.assertNull(key.getPartition4());
        Assertions.assertEquals("s", key.getSort());
    }

    @Test
    public void testOf_shouldKeepEveryValue_whenGivenFourPartitions() {
        // Act
        var key = Key.of("p1", 2, 3L, "p4", "s");

        // Verify
        Assertions.assertEquals("p1", key.getPartition1());
        Assertions.assertEquals(2, key.getPartition2());
        Assertions.assertEquals(3L, key.getPartition3());
        Assertions.assertEquals("p4", key.getPartition4());
        Assertions.assertEquals("s", key.getSort());
    }

    @Test
    public void testOf_shouldAllowNullSort_whenTableHasNoSortKey() {
        // Act
        var key = Key.of("p", null);

        // Verify
        Assertions.assertEquals("p", key.getPartition1());
        Assertions.assertNull(key.getSort());
    }

    @Test
    public void testOf_shouldThrow_whenFirstPartitionIsNull() {
        // Act / Verify
        Assertions.assertThrows(NullPointerException.class, () -> Key.of(null, "s"));
        Assertions.assertThrows(NullPointerException.class, () -> Key.of(null, "p2", "s"));
    }

    @Test
    public void testOf_shouldThrow_whenALaterPartitionIsNull() {
        // Act / Verify - the constructor does not check these, so the factories must
        Assertions.assertThrows(NullPointerException.class, () -> Key.of("p1", null, "s"));
        Assertions.assertThrows(NullPointerException.class, () -> Key.of("p1", "p2", null, "s"));
        Assertions.assertThrows(NullPointerException.class, () -> Key.of("p1", "p2", "p3", null, "s"));
    }

    @Test
    public void testEquals_shouldCompareEveryValue_whenKeysHaveSeveralPartitions() {
        // Setup
        var key = Key.of("p1", "p2", "p3", "s");

        // Verify
        Assertions.assertEquals(key, Key.of("p1", "p2", "p3", "s"));
        Assertions.assertNotEquals(key, Key.of("p1", "p2", "other", "s"));
        Assertions.assertNotEquals(key, Key.of("p1", "other", "p3", "s"));
        Assertions.assertNotEquals(key, Key.of("p1", "p2", "p3", "other"));
        // Fewer partition values is a different key, even though the leading ones match
        Assertions.assertNotEquals(key, Key.of("p1", "p2", "s"));
    }

    @Test
    public void testHashCode_shouldMatch_whenKeysAreEqual() {
        // Verify
        Assertions.assertEquals(Key.of("p", "s").hashCode(), Key.of("p", "s").hashCode());
        Assertions.assertEquals(Key.of("p1", "p2", "p3", "p4", "s").hashCode(),
            Key.of("p1", "p2", "p3", "p4", "s").hashCode());
    }
}
