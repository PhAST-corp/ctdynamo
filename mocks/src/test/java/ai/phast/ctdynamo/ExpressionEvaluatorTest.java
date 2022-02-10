package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class ExpressionEvaluatorTest {

    @Test
    public void testKey_shouldReturnTrue_onlyWhenPartitionPresent() {
        // Setup
        var expr = ConditionExpression.requirePresent("partition");

        // Act
        var partitionPresent = ExpressionEvaluator.evalBool(expr, Map.of("partition", av("x")));
        var partitionAbsent = ExpressionEvaluator.evalBool(expr, Map.of());
        var partitionWrongName = ExpressionEvaluator.evalBool(expr, Map.of("paxtition", av("x")));

        // Verify
        assert partitionPresent;
        assert !partitionAbsent;
        assert !partitionWrongName;
    }

    @Test
    public void testEvaluate_shouldThrow_whenInvalidExpression() {
        // Setup
        var expr = new ConditionExpression("a !!! b", null, null);

        // Act & Verify
        Assertions.assertThrows(RuntimeException.class, () -> ExpressionEvaluator.eval(expr, Map.of()));
    }

    @Test
    public void testKeyExpression_shouldComputeBetween_whenKeyExpression() {
        // Setup
        var expr = new ConditionExpression("#p = :p AND #s BETWEEN :s1 AND :s2",
            Map.of(":p", av("farm"), ":s1", av("cow"), ":s2", av("horse")),
            Map.of("#p", "p", "#s", "s"));

        // Act
        var tooLow = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("ant")));
        var tooHigh = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("zebra")));
        var justRight = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("dog")));
        var loBound = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("cow")));
        var hiBound = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("horse")));
        var wrongPartition = ExpressionEvaluator.evalBool(expr, Map.of("p", av("house"), "s", av("dog")));

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
        var expr = new ConditionExpression("#p = :p AND #s > :s",
            Map.of(":p", av("farm"), ":s", av("cow")),
            Map.of("#p", "p", "#s", "s"));

        // Act
        var tooLow = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("ant")));
        var highEnough = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("zebra")));
        var equal = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("cow")));
        var wrongPartition = ExpressionEvaluator.evalBool(expr, Map.of("p", av("house"), "s", av("dog")));

        // Verify
        assert !tooLow;
        assert highEnough;
        assert !equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeGreaterThanOrEqual_whenKeyExpression() {
        // Setup
        var expr = new ConditionExpression("#p = :p AND #s >= :s",
            Map.of(":p", av("farm"), ":s", av(100)),
            Map.of("#p", "p", "#s", "s"));

        // Act
        var tooLow = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av(5)));
        var highEnough = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av(500)));
        var equal = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av(100)));
        var wrongPartition = ExpressionEvaluator.evalBool(expr, Map.of("p", av("house"), "s", av(500)));

        // Verify
        assert !tooLow;
        assert highEnough;
        assert equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeLessThan_whenKeyExpression() {
        // Setup
        var expr = new ConditionExpression("#p = :p AND #s < :s",
            Map.of(":p", av(5), ":s", av("cow")),
            Map.of("#p", "p", "#s", "s"));

        // Act
        var lower = ExpressionEvaluator.evalBool(expr, Map.of("p", av(5), "s", av("ant")));
        var higher = ExpressionEvaluator.evalBool(expr, Map.of("p", av(5), "s", av("zebra")));
        var equal = ExpressionEvaluator.evalBool(expr, Map.of("p", av(5), "s", av("cow")));
        var wrongPartition = ExpressionEvaluator.evalBool(expr, Map.of("p", av(4), "s", av("ant")));

        // Verify
        assert lower;
        assert !higher;
        assert !equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeLessThanOrEqual_whenKeyExpression() {
        // Setup
        var expr = new ConditionExpression("#p = :p AND #s <= :s",
            Map.of(":p", av("farm"), ":s", av("cow")),
            Map.of("#p", "p", "#s", "s"));

        // Act
        var lower = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("ant")));
        var higher = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("zebra")));
        var equal = ExpressionEvaluator.evalBool(expr, Map.of("p", av("farm"), "s", av("cow")));
        var wrongPartition = ExpressionEvaluator.evalBool(expr, Map.of("p", av("house"), "s", av("ant")));

        // Verify
        assert lower;
        assert !higher;
        assert equal;
        assert !wrongPartition;
    }

    @Test
    public void testKeyExpression_shouldComputeNotEqual_whenExpressionAlone() {
        // Setup
        var expr = new ConditionExpression("#v <> :v",
            Map.of(":v", av("cow")),
            Map.of("#v", "v"));

        // Act
        var notEqual = ExpressionEvaluator.evalBool(expr, Map.of("v", av("television")));
        var equal = ExpressionEvaluator.evalBool(expr, Map.of("v", av("cow")));

        // Verify
        assert !equal;
        assert notEqual;
    }

    @Test
    public void testKeyExpression_shouldComputeContains_whenProvidedStrings() {
        // Setup
        var expr = new ConditionExpression("contains(:v, #v)",
            Map.of(":v", av("the lazy dog jumped")),
            Map.of("#v", "v"));

        // Act
        var containsDog = ExpressionEvaluator.evalBool(expr, Map.of("v", av(" dog ")));
        var containsCat = ExpressionEvaluator.evalBool(expr, Map.of("v", av(" cat ")));

        // Verify
        assert containsDog;
        assert !containsCat;
    }

    @Test
    public void testKeyExpression_shouldComputeBeginsWith_whenProvidedStrings() {
        // Setup
        var expr = new ConditionExpression("begins_with(:v, #v)",
            Map.of(":v", av("the lazy dog jumped")),
            Map.of("#v", "v"));

        // Act
        var startsWithThe = ExpressionEvaluator.evalBool(expr, Map.of("v", av("the")));
        var startsWithDog = ExpressionEvaluator.evalBool(expr, Map.of("v", av("dog")));

        // Verify
        assert startsWithThe;
        assert !startsWithDog;
    }

    private AttributeValue av(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue av(int value) {
        return AttributeValue.builder().n(Integer.toString(value)).build();
    }
}
