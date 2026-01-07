package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.NoIndex;
import ai.phast.ctdynamo.tables.NoIndexDynamoTable;
import ai.phast.ctdynamo.tables.NoSortKey;
import ai.phast.ctdynamo.tables.NoSortKeyDynamoTable;
import ai.phast.ctdynamo.tables.RenamedIndexDynamoTable;
import ai.phast.ctdynamo.tables.WithIndex;
import ai.phast.ctdynamo.tables.WithIndexDynamoTable;
import ai.phast.ctdynamo.tables.WithStringSet;
import ai.phast.ctdynamo.tables.WithStringSetDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MockDynamoClientTest {

    @Test
    public void testTable_shoudSeeItem_afterAdd() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("x", 100, true));

        // Act
        table.putItem(new NoIndex("y", 50, false));

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("x", 100, true), new NoIndex("y", 50, false));
    }

    @Test
    public void testTable_shoudReplaceItem_afterAddWithSameKeys() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("x", 100, true));

        // Act
        table.putItem(new NoIndex("x", 100, false));

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("x", 100, false));
    }

    @Test
    public void testUpdateItem_shouldReturnErrorWithNoItem_whenHasItemCalled() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class);

        // Act and validate
        try {
            table.updateItem("x", null, new ConditionExpression(
                            "#ip1 = :ip1 AND contains(:validIP2, #ip2)",
                            Map.of(":ip1", av(1), ":validIP2", av(List.of("1", "2"))),
                            Map.of("#ip1", "ip1", "#ip2", "ip2")),
                    Map.of("ip1", ":ip1"), Map.of(":ip1", av(1)), null, false);
        Assertions.fail();
        } catch (ConditionalCheckFailedException e) {
            Assertions.assertFalse(e.hasItem());
            Assertions.assertEquals(e.item(), Map.of());
        }
    }

    @Test
    public void testUpdateItem_shouldAddItemToSet_whenAddItemToSetSyntaxUsed() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithStringSetDynamoTable.class,
            new WithStringSet("whatever", 10, null));

        // Act
        var item = table.updateItem("whatever", 10, null, Map.of("stringSet", "+:newString"),
            Map.of(":newString", AttributeValue.fromSs(List.of("hi"))),
            Collections.emptyMap(), false).getItem();

        // Verify
        Assertions.assertEquals(1, item.getStringSet().size());
        Assertions.assertEquals("hi", item.getStringSet().get(0));
    }

    @Test
    public void testPutItem_shouldNotReplace_whenConditionExpressionPrevents() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("x", 100, true));

        // Act and validate
        Assertions.assertThrows(ConditionalCheckFailedException.class,
            () -> table.putItemExtended(new NoIndex("x", 100, false),
                ConditionExpression.requireAbsent(table), false));

        // Validate: Make sure table didn't change
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("x", 100, true));
    }

    @Test
    public void testQuery_shouldFindItems_withPrimaryKey() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("x", 100, true),
            new NoIndex("x", 110, true),
            new NoIndex("y", 105, true));

        // Act
        var results = table.query("x").invoke().stream().collect(Collectors.toSet());

        // Verify
        Assertions.assertEquals(Set.of(new NoIndex("x", 100, true), new NoIndex("x", 110, true)), results);
    }

    @Test
    public void testQuery_shouldFindItems_withRangeKey() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("x", 100, true),
            new NoIndex("x", 110, true),
            new NoIndex("y", 105, true));

        // Act
        var results = table.query("x").sortBetween(98, 102).invoke().stream().collect(Collectors.toSet());

        // Verify
        Assertions.assertEquals(Set.of(new NoIndex("x", 100, true)), results);
    }

    @Test
    public void testQuery_shouldFindItems_inIndex() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("px", "sa", "ipx", "ipa"),
            new WithIndex("px", "sb", "ipx", "ipa"),
            new WithIndex("py", "sa", "ipy", "ipa"),
            new WithIndex("pz", "sc", "ipy", "ipb"));
        var index = table.getIIndex();

        // Act
        var ipxItems = index.query("ipx").invoke().stream().collect(Collectors.toSet());
        var ipyItems = index.query("ipy").invoke().stream().collect(Collectors.toSet());

        // Verify
        Assertions.assertEquals(ipxItems, Set.of(
            new WithIndex("px", "sa", "ipx", "ipa"),
            new WithIndex("px", "sb", "ipx", "ipa")));
        Assertions.assertEquals(ipyItems, Set.of(
            new WithIndex("py", "sa", "ipy", "ipa"),
            new WithIndex("pz", "sc", "ipy", "ipb")));
    }

    @Test
    public void testIndex_shouldUpdate_whenUpdatingOldItemIndexPartitionKey() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
                new WithIndex("px", "sa", "ipx", "ipa"));
        var index = table.getIIndex();
        // Force index population
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipx", "ipa")),
                index.query("ipx").invoke().stream().collect(Collectors.toList()));

        // Act
        table.updateItem(new WithIndex("px", "sa", "ipy", "ipa"), null, null, null, null, false);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new WithIndex("px", "sa", "ipy", "ipa"));
        Assertions.assertEquals(List.of(), index.query("ipx").invoke().stream().collect(Collectors.toList()));
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipy", "ipa")),
                index.query("ipy").invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testIndex_shouldUpdate_whenUpdatingOldItemIndexSortKey() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
                new WithIndex("px", "sa", "ipx", "ipa"));
        var index = table.getIIndex();
        // Force index population
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipx", "ipa")),
                index.query("ipx").invoke().stream().collect(Collectors.toList()));

        // Act
        table.updateItem(new WithIndex("px", "sa", "ipx", "ipb"), null, null, null, null, false);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new WithIndex("px", "sa", "ipx", "ipb"));
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipx", "ipb")), index.query("ipx").invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testIndex_shouldUpdate_whenItemAddedReplacesOldItem() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("px", "sa", "ipx", "ipa"));
        var index = table.getIIndex();
        // Force index population
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipx", "ipa")),
            index.query("ipx").invoke().stream().collect(Collectors.toList()));

        // Act
        table.putItem(new WithIndex("px", "sa", "ipy", "ipa"));

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new WithIndex("px", "sa", "ipy", "ipa"));
        Assertions.assertEquals(List.of(), index.query("ipx").invoke().stream().collect(Collectors.toList()));
        Assertions.assertEquals(List.of(new WithIndex("px", "sa", "ipy", "ipa")),
            index.query("ipy").invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testIndex_shouldBeSparse_whenIndexesAreNull() {
        // Setup
        var items = List.of(
            new WithIndex("px", "sa", "ipx", "ipa"),
            new WithIndex("px", "sb", null, "ipa"),
            new WithIndex("px", "sc", "ipx", null));
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class, items);
        var index = table.getIIndex();

        // Act
        var ipxValues = index.query("ipx").invoke().stream().collect(Collectors.toList());

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, items);
        Assertions.assertEquals(ipxValues, items.subList(0, 1));
    }

    @Test
    public void TestIndex_shouldUpdate_whenDeleteFromTable() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class, new WithIndex("a", "b", "c", "d"),
            new WithIndex("e", "f", "g", "h"),
            new WithIndex("x", "y", "c", "d"));
        var index = table.getIIndex();
        // Force population of the index table
        Assertions.assertEquals(Set.of(new WithIndex("a", "b", "c", "d"),
            new WithIndex("x", "y", "c", "d")),
            index.query("c").invoke().stream().collect(Collectors.toSet()));

        // Act
        table.deleteItem("x", "y");

        // Verify
        Assertions.assertEquals(List.of(new WithIndex("a", "b", "c", "d")),
            index.query("c").invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testGetItem_shouldFindItem_whenInTable() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class,
            new NoSortKey("a", 5, 10, Instant.ofEpochMilli(1000000)));

        // Act
        var match = table.getItem("a", null);
        var noMatch = table.getItem("b", null);

        // Verify
        Assertions.assertEquals(new NoSortKey("a", 5, 10, Instant.ofEpochMilli(1000000)), match);
        Assertions.assertNull(noMatch);
    }

    @Test
    public void testIndex_shouldFindProperItems_whenInTableWithNoSortKey() {
        // Setup
        var t1 = Instant.parse("2021-05-20T10:00:00Z");
        var t2 = Instant.parse("2021-05-20T11:00:00Z");
        var table = DynamoMockUtil.buildMockTable(NoSortKeyDynamoTable.class,
            new NoSortKey("a", 5, 10, t1),
            new NoSortKey("b", 5, 15, t2));
        var i1 = table.getI1Index();
        var i2 = table.getI2Index();
        // Force instantiation of the indexes
        Assertions.assertEquals(List.of(new NoSortKey("a", 5, 10, t1), new NoSortKey("b", 5, 15, t2)),
            i1.query(5).invoke().stream().collect(Collectors.toList()));
        Assertions.assertEquals(List.of(new NoSortKey("a", 5, 10, t1)),
            i2.query(10).invoke().stream().collect(Collectors.toList()));

        // Act: Mutate the database. A bunch
        table.putItem(new NoSortKey("c", 10, 20, t1));
        table.putBatch(List.of(new NoSortKey("d", 15, 100, t2),
            new NoSortKey("b", 7, 15, t2),
            new NoSortKey("e", 5, 15, t2)));
        table.deleteBatchByKey(List.of(new Key<>("e", null), new Key<>("a", null)));

        // Verify
        Assertions.assertEquals(List.of(), i1.query(5).invoke().stream().collect(Collectors.toList()));
        Assertions.assertEquals(List.of(new NoSortKey("b", 7, 15, t2)), i1.query(7).invoke().stream().collect(Collectors.toList()));
        Assertions.assertEquals(List.of(new NoSortKey("b", 7, 15, t2)), i2.query(15).invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testBatchOperations_shouldPutDeleteAndGetCorrectly_whenUsed() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class);
        var index = table.getIIndex();
        Assertions.assertEquals(0L, index.query("x").invoke().stream().count()); // Just enable the index

        // Act
        table.putBatch(IntStream.range(0, 123)
                           .mapToObj(i -> new WithIndex("p" + (i % 11), "s" + (i % 13), "ip" + (i % 17), "is" + (i % 7)))
                           .collect(Collectors.toList()));
        table.deleteBatchByKey(IntStream.range(0, 123)
                                   .filter(i -> (i % 2) == 0)
                                   .mapToObj(i -> new Key<>("p" + (i % 11), "s" + (i % 13)))
                                   .collect(Collectors.toList()));
        var result = table.getBatchByKeyExtended(IntStream.range(0, 123)
                                                     .filter(i -> (i % 3) == 0)
                                                     .mapToObj(i -> new Key<>("p" + (i % 11), "s" + (i % 13)))
                                                     .collect(Collectors.toList()), false);

        // Verify
        Assertions.assertEquals(IntStream.range(0, 123)
                                    .filter(i -> (i % 3) == 0 && (i % 2) != 0)
                                    .mapToObj(i -> new WithIndex("p" + (i % 11), "s" + (i % 13), "ip" + (i % 17), "is" + (i % 7)))
                                    .collect(Collectors.toSet()),
            new HashSet<>(result.getItems()));
        Assertions.assertEquals(
            IntStream.range(0, 123)
                .filter(i -> (i % 3) == 0 && (i % 2) == 0)
                .mapToObj(i -> new Key<>("p" + (i % 11), "s" + (i % 13)))
                .collect(Collectors.toSet()),
            new HashSet<>(result.getUnprocessedValues()));
        Assertions.assertEquals(IntStream.range(0, 123)
                                    .filter(i -> (i % 2) != 0 && (i % 17) == 0)
                                    .mapToObj(i -> new WithIndex("p" + (i % 11), "s" + (i % 13), "ip0", "is" + (i % 7)))
                                    .sorted(Comparator.comparing(WithIndex::getIs))
                                    .collect(Collectors.toList()),
            index.query("ip0").invoke().stream().collect(Collectors.toList()));
    }

    @Test
    public void testQuery_shouldFindAllWithEqualSort_onlyWhenInclusiveGreater() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("p0", "s", "x", "a"),
            new WithIndex("p1", "s", "x", "b"),
            new WithIndex("p2", "s", "x", "b"),
            new WithIndex("p3", "s", "x", "c"));
        var index = table.getIIndex();

        // Act
        var greaterThanB = index.query("x").sortGreaterThan("b").stream().collect(Collectors.toList());
        var greaterThanOrEqualToB = index.query("x").sortGreaterThanOrEqual("b").stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(new WithIndex("p3", "s", "x", "c")), greaterThanB);
        Assertions.assertEquals(List.of(new WithIndex("p1", "s", "x", "b"),
            new WithIndex("p2", "s", "x", "b"),
            new WithIndex("p3", "s", "x", "c")), greaterThanOrEqualToB);
    }

    @Test
    public void testQuery_shouldFindAllWithEqualSort_onlyWhenInclusiveLess() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("p0", "s", "x", "a"),
            new WithIndex("p1", "s", "x", "b"),
            new WithIndex("p2", "s", "x", "b"),
            new WithIndex("p3", "s", "x", "c"));
        var index = table.getIIndex();

        // Act
        var greaterThanB = index.query("x").sortLessThan("b").stream().collect(Collectors.toList());
        var greaterThanOrEqualToB = index.query("x").sortLessThanOrEqual("b").stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(new WithIndex("p0", "s", "x", "a")), greaterThanB);
        Assertions.assertEquals(List.of(new WithIndex("p0", "s", "x", "a"),
            new WithIndex("p1", "s", "x", "b"),
            new WithIndex("p2", "s", "x", "b")), greaterThanOrEqualToB);
    }

    @Test
    public void testQuery_shouldFindAllWithPrefix_whenPrefixUsed() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("x", "aaa", "x", "a"),
            new WithIndex("x", "ab", "x", "a"),
            new WithIndex("x", "abb", "x", "a"),
            new WithIndex("x", "abc", "x", "a"),
            new WithIndex("x", "acc", "x", "a"));

        // Act
        var withAa = table.query("x").sortStartsWith("ab").stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(
            new WithIndex("x", "ab", "x", "a"),
            new WithIndex("x", "abb", "x", "a"),
            new WithIndex("x", "abc", "x", "a")), withAa);
    }

    @Test
    public void testQuery_shouldReturnEmpty_whenLowAboveHigh() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("x", "dog", "x", "a"));

        // Act
        var queryResult = table.query("x").sortBetween("zebra", "ant").stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(), queryResult);
    }

    @Test
    public void testQuery_shouldReturnReverseOrder_whenScanBackward() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("x", "aaa", "x", "a"),
            new WithIndex("x", "ab", "x", "a"),
            new WithIndex("x", "abb", "x", "a"),
            new WithIndex("x", "abc", "x", "a"),
            new WithIndex("x", "acc", "x", "a"));

        // Act
        var withAa = table.query("x").sortStartsWith("ab").scanForward(false)
                         .stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(
            new WithIndex("x", "abc", "x", "a"),
            new WithIndex("x", "abb", "x", "a"),
            new WithIndex("x", "ab", "x", "a")), withAa);
    }

    @Test
    public void testQuery_shouldSkipNonmatchingItems_whenFilterExpressionUsed() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(WithIndexDynamoTable.class,
            new WithIndex("x", "a", "yes", null),
            new WithIndex("y", "a", "yes", null),
            new WithIndex("y", "b", "no", null),
            new WithIndex("y", "c", "yes", null),
            new WithIndex("y", "d", "maybe", null));

        // Act
        var resultWithYes = table.query("y")
                          .filter(new ConditionExpression("#ip = :ip",
                              Map.of(":ip", AttributeValue.builder().s("yes").build()), Map.of("#ip", "ip")))
                          .stream().collect(Collectors.toList());

        // Verify
        Assertions.assertEquals(List.of(new WithIndex("y", "a", "yes", null), new WithIndex("y", "c", "yes", null)),
            resultWithYes);
    }

    @Test
    public void testDeleteExtended_shouldDelete_whenConditionPasses() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("a", 10, true));

        // Act
        table.deleteItemExtended("a", 10, ConditionExpression.requirePresent("bVal"), false);

        // Verify
        DynamoMockUtil.verifyContainsExactly(table);
    }

    @Test
    public void testDeleteExtended_shouldThrowAndNotDelete_whenConditionFails() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class,
            new NoIndex("a", 10, true));
        var expr = new ConditionExpression("NOT #bVal", null, Map.of("#bVal", "bVal"));

        // Act & Verify
        Assertions.assertThrows(ConditionalCheckFailedException.class, () -> table.deleteItemExtended("a", 10, expr, false));

        // Verify
        DynamoMockUtil.verifyContainsExactly(table, new NoIndex("a", 10, true));
    }

    @Test
    public void testTableCreate_shoudThrowException_whenTwoItemsWithSameKeys() {
        // Act & verify
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> DynamoMockUtil.buildMockTable(
                NoIndexDynamoTable.class,
                new NoIndex("x", 100, true), new NoIndex("x", 100, false)));
    }

    @Test
    public void testTransactions_shouldExecute_whenNoConflicts() {
        // Setup
        var table1 = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class, "table1",
            new NoIndex("a", 10, true),
            new NoIndex("b", 11, false));
        var table2 = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class, "table2",
            new NoIndex("a", 10, true));

        // Act
        var transaction = new Transaction(table1);
        var putCheck = transaction.put(table2, new NoIndex("c", 12, false),
            ConditionExpression.requireAbsent(table2));
        var updateCheck = transaction.update(table1, "a", 10, null,
            Map.of("bVal", ":false"),
            Map.of(":false", AttributeValue.builder().bool(false).build()),
            null);
        var deleteCheck = transaction.delete(table2, "a", 10,
            ConditionExpression.requirePresent(table2));
        var checkCheck = transaction.check(table1, "b", 11,
            new ConditionExpression("bVal = :false", Map.of(":false", AttributeValue.builder().bool(false).build()),
                null));
        transaction.execute();

        // Verify
        Assertions.assertNull(putCheck.getErrorType());
        Assertions.assertNull(updateCheck.getErrorType());
        Assertions.assertNull(deleteCheck.getErrorType());
        Assertions.assertNull(checkCheck.getErrorType());
        DynamoMockUtil.verifyContainsExactly(table1,
            new NoIndex("a", 10, false),
            new NoIndex("b", 11, false));
        DynamoMockUtil.verifyContainsExactly(table2,
            new NoIndex("c", 12, false));
    }

    @Test
    public void testTransactions_shouldReportFailuresAndNotUpdate_whenConflictsPresent() {
        // Setup
        var table1 = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class, "table1",
            new NoIndex("a", 10, true),
            new NoIndex("b", 11, true));
        var table2 = DynamoMockUtil.buildMockTable(NoIndexDynamoTable.class, "table2",
            new NoIndex("a", 10, true),
            new NoIndex("c", 12, true));

        // Act
        var transaction = new Transaction(table1);
        var putCheck = transaction.put(table2, new NoIndex("c", 12, false),
            ConditionExpression.requireAbsent(table2));
        var updateCheck = transaction.update(table1, "a", 10, null,
            Map.of("bVal", ":false"),
            Map.of(":false", AttributeValue.builder().bool(false).build()),
            null);
        var deleteCheck = transaction.delete(table2, "a", 10,
            ConditionExpression.requirePresent(table2));
        var checkCheck = transaction.check(table1, "b", 11,
            new ConditionExpression("bVal = :false", Map.of(":false", AttributeValue.builder().bool(false).build()),
                null));
        Assertions.assertThrows(TransactionCanceledException.class, transaction::execute);

        // Verify
        Assertions.assertEquals(Transaction.ErrorType.CONDITION_CHECK, putCheck.getErrorType());
        Assertions.assertEquals(new NoIndex("c", 12, true), putCheck.getFailedItem());
        Assertions.assertNull(updateCheck.getErrorType());
        Assertions.assertNull(deleteCheck.getErrorType());
        Assertions.assertEquals(Transaction.ErrorType.CONDITION_CHECK, checkCheck.getErrorType());
        Assertions.assertEquals(new NoIndex("b", 11, true), checkCheck.getFailedItem());
        DynamoMockUtil.verifyContainsExactly(table1,
            new NoIndex("a", 10, true),
            new NoIndex("b", 11, true));
        DynamoMockUtil.verifyContainsExactly(table2,
            new NoIndex("a", 10, true),
            new NoIndex("c", 12, true));
    }

    @Test
    public void testRenamedIndex_shouldHaveDifferentNames_whenIndexRenamed() {
        // Setup
        var table = DynamoMockUtil.buildMockTable(RenamedIndexDynamoTable.class);
        var colorIndex = table.getColorIndex();

        // Verify
        Assertions.assertEquals("color5", colorIndex.getIndexName());
        Assertions.assertEquals(colorIndex.getClass(), table.getIndex("color5", String.class, Float.class).getClass());
    }

    private AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }

    private AttributeValue av(List<String> values) {
        return AttributeValue.builder().l(values.stream()
                .map(v -> AttributeValue.builder().n(v).build()).collect(Collectors.toList())).build();
    }
}
