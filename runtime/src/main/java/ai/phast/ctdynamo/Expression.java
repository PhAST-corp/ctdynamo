package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/**
 * A dynamo expression. The ConditionExpression is used to fail operations, base Expression is used in updates to compute
 * the new value of an attribute.
 */
public class Expression {

    /** Text of the expression */
    private final String expression;

    /** Map of values referenced by the expression */
    private final Map<String, AttributeValue> values;

    /** Map of attribute names referenced by the expression */
    private final Map<String, String> attributeNames;

    /**
     * Build an expression
     * @param expression The text of the expression
     * @param values The optional values of the expression
     * @param attributeNames The optional attribute names referenced by the expression
     */
    protected Expression(String expression, Map<String, AttributeValue> values, Map<String, String> attributeNames) {
        this.expression = expression;
        this.values = values;
        this.attributeNames = attributeNames;
    }

    /**
     * Get the text of the expression
     * @return The text of the expression
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Get the values referenced by the expression. All values added automatically by ctDynamo will begin with "ctdynamo_"
     * @return The values references by the expression
     */
    public Map<String, AttributeValue> getValues() {
        return values;
    }

    /**
     * Get the attribute names references by the expression
     * @return The attribute names reerenced by the expression. All names used by ctDynamo will be the attribute name with
     *         a '#' character in front
     */
    public Map<String, String> getAttributeNames() {
        return attributeNames;
    }
}
