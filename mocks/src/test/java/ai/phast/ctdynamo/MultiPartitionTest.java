package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.MultiPartition;
import ai.phast.ctdynamo.tables.MultiPartitionDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests of the code the processor generates for indexes with more than one partition key
 */
public class MultiPartitionTest {

    @Test
    public void testIndex_shouldOrderPartitionKeysByOrderAttribute_whenDeclaredOutOfOrder() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);

        // Act
        var trio = table.getTrioIndex();
        var quad = table.getQuadIndex();

        // Verify - "order", not declaration order, decides the position
        Assertions.assertEquals(List.of("tenant", "region", "category"), trio.getPartitionKeyAttributes());
        Assertions.assertEquals(List.of("team", "tenant", "shard", "status"), quad.getPartitionKeyAttributes());
    }

    @Test
    public void testIndex_shouldPlaceAttributeDifferently_whenOrdersDifferPerIndex() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);
        var item = new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01");

        // Act / Verify - "tenant" is order 1 in trio but order 2 in quad
        Assertions.assertEquals("acme", table.getTrioIndex().getPartitionValue1(item));
        Assertions.assertEquals("acme", table.getQuadIndex().getPartitionValue2(item));
        Assertions.assertEquals("red", table.getQuadIndex().getPartitionValue1(item));
    }

    @Test
    public void testGetPartitionValue_shouldReadEachKey_whenIndexHasSeveral() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);
        var item = new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01");

        // Act
        var trio = table.getTrioIndex();

        // Verify
        Assertions.assertEquals("acme", trio.getPartitionValue1(item));
        Assertions.assertEquals(7, trio.getPartitionValue2(item));
        Assertions.assertEquals("cat", trio.getPartitionValue3(item));
        // A three-key index must report null for the fourth slot; that is how the runtime tells how many it has
        Assertions.assertNull(trio.getPartitionValue4(item));
        Assertions.assertEquals("2026-01-01", trio.getSortValue(item));
    }

    @Test
    public void testQuery_shouldMatchOnEveryPartitionKey_whenIndexHasThree() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class,
            new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01"),
            new MultiPartition("id2", "cat", "acme", "green", 7, "red", 42L, "2026-01-02"),
            // Differs from id1 in exactly one partition key, so each of these must be excluded
            new MultiPartition("id3", "dog", "acme", "green", 7, "red", 42L, "2026-01-03"),
            new MultiPartition("id4", "cat", "other", "green", 7, "red", 42L, "2026-01-04"),
            new MultiPartition("id5", "cat", "acme", "green", 8, "red", 42L, "2026-01-05"));

        // Act
        var found = table.getTrioIndex().query("acme", 7, "cat").invoke().stream()
                        .map(MultiPartition::getId)
                        .collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of("id1", "id2"), found);
    }

    @Test
    public void testQuery_shouldSortAndBound_whenIndexHasSeveralPartitionKeys() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class,
            new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01"),
            new MultiPartition("id2", "cat", "acme", "green", 7, "red", 42L, "2026-01-02"),
            new MultiPartition("id3", "cat", "acme", "green", 7, "red", 42L, "2026-01-03"));

        // Act
        var found = table.getTrioIndex().query("acme", 7, "cat")
                        .sortGreaterThan("2026-01-01")
                        .invoke().stream()
                        .map(MultiPartition::getId)
                        .collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of("id2", "id3"), found);
    }

    @Test
    public void testQuery_shouldUseAllFourKeys_whenIndexHasFourAndNoSortKey() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class,
            new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01"),
            new MultiPartition("id2", "cat", "acme", "green", 7, "red", 99L, "2026-01-02"),
            new MultiPartition("id3", "cat", "acme", "blue", 7, "red", 42L, "2026-01-03"));

        // Act
        var found = table.getQuadIndex().query("red", "acme", 42L, "green").invoke().stream()
                        .map(MultiPartition::getId)
                        .collect(Collectors.toList());

        // Verify - id2 differs in shard, id3 differs in status
        Assertions.assertEquals(List.of("id1"), found);
    }

    @Test
    public void testQuery_shouldMatchOneSortValue_whenSortEquals() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class,
            new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01"),
            new MultiPartition("id2", "cat", "acme", "green", 7, "red", 42L, "2026-01-02"),
            new MultiPartition("id3", "cat", "acme", "green", 7, "red", 42L, "2026-01-03"));

        // Act
        var found = table.getTrioIndex().query("acme", 7, "cat")
                        .sortEquals("2026-01-02")
                        .invoke().stream()
                        .map(MultiPartition::getId)
                        .collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of("id2"), found);
    }

    @Test
    public void testIndex_shouldBeANameableType_whenGenerated() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class,
            new MultiPartition("id1", "cat", "acme", "green", 7, "red", 42L, "2026-01-01"));

        // Act - the generated index class is public, so callers can name it instead of writing out the six type
        // arguments of the DynamoIndex it extends. This must compile without "var"
        MultiPartitionDynamoTable.TrioIndex trio = table.getTrioIndex();
        var found = trio.query("acme", 7, "cat").invoke().stream()
                        .map(MultiPartition::getId)
                        .collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of("id1"), found);
    }

    @Test
    public void testGetIndex_shouldReturnIndex_whenAllKeyClassesMatch() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);

        // Act
        var byName = table.getIndex("trio", String.class, Integer.class, String.class, Void.class, String.class);

        // Verify
        Assertions.assertEquals(table.getTrioIndex().getClass(), byName.getClass());
        Assertions.assertEquals("trio", byName.getIndexName());
    }

    @Test
    public void testGetIndex_shouldThrow_whenAKeyClassIsWrong() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);

        // Act / Verify - the third partition key of "trio" is a String, not a Long
        var thrown = Assertions.assertThrows(IllegalArgumentException.class,
            () -> table.getIndex("trio", String.class, Integer.class, Long.class, Void.class, String.class));
        Assertions.assertEquals("Incorrect key types for index trio"
            + ", expected: String, Integer, String, Void, String"
            + ", got: String, Integer, Long, Void, String", thrown.getMessage());
    }

    @Test
    public void testGetIndex_shouldReportUncheckedKeysAsAny_whenSomeClassesAreNull() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);

        // Act / Verify - the keys we passed null for read as "any" in the message
        var thrown = Assertions.assertThrows(IllegalArgumentException.class,
            () -> table.getIndex("trio", Long.class, null, null, null, null));
        Assertions.assertEquals("Incorrect key types for index trio"
            + ", expected: String, Integer, String, Void, String"
            + ", got: Long, any, any, any, any", thrown.getMessage());
    }

    @Test
    public void testGetIndex_shouldSkipUncheckedKeys_whenClassesAreNull() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(MultiPartitionDynamoTable.class);

        // Act - a null class means "do not check this one"
        var byName = table.getIndex("quad", String.class, null, null, null, null);

        // Verify
        Assertions.assertEquals("quad", byName.getIndexName());
    }
}
