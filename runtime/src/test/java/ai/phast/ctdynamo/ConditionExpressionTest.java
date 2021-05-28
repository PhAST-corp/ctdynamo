package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class ConditionExpressionTest {

    @Test
    public void testRequirePresent_shouldBuildExpression_whenCalled() {
        // Act
        var expr = ConditionExpression.requirePresent("attrName");

        // Verify
        Assertions.assertEquals(expr.getExpression(), "attribute_exists(#attrName)");
        Assertions.assertEquals(expr.getAttributeNames(), Map.of("#attrName", "attrName"));
    }

    @Test
    public void testRequireAbsent_shouldBuildExpression_whenCalled() {
        // Act
        var expr = ConditionExpression.requireAbsent("attrName");

        // Verify
        Assertions.assertEquals(expr.getExpression(), "attribute_not_exists(#attrName)");
        Assertions.assertEquals(expr.getAttributeNames(), Map.of("#attrName", "attrName"));
    }

    @Test
    public void testAnd_shouldCombineExprAndAttrs_whenUsed() {
        // Setup
        var e1 = ConditionExpression.requirePresent("a1");
        var e2 = ConditionExpression.requireAbsent("a2");
        var e3 = new ConditionExpression("#a3 = :val", Map.of(":val", AttributeValue.builder().s("val").build()), Map.of("#a3", "a3"));

        // Act
        var expr = ConditionExpression.and(e1, e2, e3);

        // Verify
        Assertions.assertEquals(expr.getExpression(), "(attribute_exists(#a1)) AND (attribute_not_exists(#a2)) AND (#a3 = :val)");
        Assertions.assertEquals(expr.getValues(), Map.of(":val", AttributeValue.builder().s("val").build()));
        Assertions.assertEquals(expr.getAttributeNames(), Map.of("#a1", "a1", "#a2", "a2", "#a3", "a3"));
    }
}
