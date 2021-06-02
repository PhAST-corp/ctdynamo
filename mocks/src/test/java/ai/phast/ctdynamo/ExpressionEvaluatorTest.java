package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.NoIndexDynamoTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class ExpressionEvaluatorTest {

    @Test
    public void testKey_shouldReturnTrue_onlyWhenPartitionPresent() {
        // Setup
        var eval = buildEval(ConditionExpression.requirePresent("partition"));

        // Act
        var partitionPresent = eval.evalBool(Map.of("partition", av("x")));
        var partitionAbsent = eval.evalBool(Map.of());
        var partitionWrongName = eval.evalBool(Map.of("paxtition", av("x")));

        // Verify
        assert partitionPresent;
        assert !partitionAbsent;
        assert !partitionWrongName;
    }

    @Test
    public void testEvaluate_shouldThrow_whenInvalidExpression() {
        // Setup
        var eval = new ExpressionEvaluator("a !!! b", null, null);

        // Act & Verify
        Assertions.assertThrows(RuntimeException.class, () -> eval.eval(Map.of()));
    }

    @Test
    public void testKeyExpression_shouldComputeBetween_whenKeyExpression() {
        // Setup
        var eval = new ExpressionEvaluator("#p = :p AND #s BETWEEN :s1 AND :s2",
            Map.of("#p", "p", "#s", "s"),
            Map.of(":p", av("farm"), ":s1", av("cow"), ":s2", av("horse")));

        // Act
        var tooLow = eval.evalBool(Map.of("p", av("farm"), "s", av("ant")));
        var tooHigh = eval.evalBool(Map.of("p", av("farm"), "s", av("zebra")));
        var justRight = eval.evalBool(Map.of("p", av("farm"), "s", av("dog")));
        var loBound = eval.evalBool(Map.of("p", av("farm"), "s", av("cow")));
        var hiBound = eval.evalBool(Map.of("p", av("farm"), "s", av("horse")));
        var wrongPartition = eval.evalBool(Map.of("p", av("house"), "s", av("dog")));

        // Verify
        assert !tooLow;
        assert !tooHigh;
        assert justRight;
        assert loBound;
        assert hiBound;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeGreaterThan_whenKeyExpression() {
        // Setup
        var eval = new ExpressionEvaluator("#p = :p AND #s > :s",
            Map.of("#p", "p", "#s", "s"),
            Map.of(":p", av("farm"), ":s", av("cow")));

        // Act
        var tooLow = eval.evalBool(Map.of("p", av("farm"), "s", av("ant")));
        var highEnough = eval.evalBool(Map.of("p", av("farm"), "s", av("zebra")));
        var equal = eval.evalBool(Map.of("p", av("farm"), "s", av("cow")));
        var wrongPartition = eval.evalBool(Map.of("p", av("house"), "s", av("dog")));

        // Verify
        assert !tooLow;
        assert highEnough;
        assert !equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeGreaterThanOrEqual_whenKeyExpression() {
        // Setup
        var eval = new ExpressionEvaluator("#p = :p AND #s >= :s",
            Map.of("#p", "p", "#s", "s"),
            Map.of(":p", av("farm"), ":s", av(100)));

        // Act
        var tooLow = eval.evalBool(Map.of("p", av("farm"), "s", av(5)));
        var highEnough = eval.evalBool(Map.of("p", av("farm"), "s", av(500)));
        var equal = eval.evalBool(Map.of("p", av("farm"), "s", av(100)));
        var wrongPartition = eval.evalBool(Map.of("p", av("house"), "s", av(500)));

        // Verify
        assert !tooLow;
        assert highEnough;
        assert equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeLessThan_whenKeyExpression() {
        // Setup
        var eval = new ExpressionEvaluator("#p = :p AND #s < :s",
            Map.of("#p", "p", "#s", "s"),
            Map.of(":p", av(5), ":s", av("cow")));

        // Act
        var lower = eval.evalBool(Map.of("p", av(5), "s", av("ant")));
        var higher = eval.evalBool(Map.of("p", av(5), "s", av("zebra")));
        var equal = eval.evalBool(Map.of("p", av(5), "s", av("cow")));
        var wrongPartition = eval.evalBool(Map.of("p", av(4), "s", av("ant")));

        // Verify
        assert lower;
        assert !higher;
        assert !equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeLessThanOrEqual_whenKeyExpression() {
        // Setup
        var eval = new ExpressionEvaluator("#p = :p AND #s <= :s",
            Map.of("#p", "p", "#s", "s"),
            Map.of(":p", av("farm"), ":s", av("cow")));

        // Act
        var lower = eval.evalBool(Map.of("p", av("farm"), "s", av("ant")));
        var higher = eval.evalBool(Map.of("p", av("farm"), "s", av("zebra")));
        var equal = eval.evalBool(Map.of("p", av("farm"), "s", av("cow")));
        var wrongPartition = eval.evalBool(Map.of("p", av("house"), "s", av("ant")));

        // Verify
        assert lower;
        assert !higher;
        assert equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeNotEqual_whenExpressionAlone() {
        // Setup
        var eval = new ExpressionEvaluator("#v <> :v",
            Map.of("#v", "v"),
            Map.of(":v", av("cow")));

        // Act
        var notEqual = eval.evalBool(Map.of("v", av("television")));
        var equal = eval.evalBool(Map.of("v", av("cow")));

        // Verify
        assert !equal;
        assert notEqual;
    }

    private ExpressionEvaluator buildEval(Expression expr) {
        return new ExpressionEvaluator(expr.getExpression(), expr.getAttributeNames(), expr.getValues());
    }

    private AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }
}
