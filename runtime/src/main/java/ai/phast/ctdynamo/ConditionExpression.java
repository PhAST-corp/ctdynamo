package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An expression that returns a boolean (true/false) value. Also adds helpfer functions to build condition expressions
 */
public class ConditionExpression {

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
    public ConditionExpression(String expression, Map<String, AttributeValue> values, Map<String, String> attributeNames) {
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

    /**
     * Combine multiple subexpressions with a logical or operation
     * @param subexpressions The subexpressions to join
     * @return A single expression that will evaluate to true if any subexpression is true
     */
    public static ConditionExpression or(ConditionExpression... subexpressions) {
        return join(") OR (", Arrays.asList(subexpressions));
    }

    /**
     * Combine multiple subexpressions with a logical or operation
     * @param subexpressions The subexpressions to join
     * @return A single expression that will evaluate to true if any subexpression is true
     */
    public static ConditionExpression or(Collection<ConditionExpression> subexpressions) {
        return join(") OR (", subexpressions);
    }

    /**
     * Combine multiple subexpressions with a logical and operation
     * @param subexpressions The subexpressions to join
     * @return A single expression that will evaluate to true if all subexpressions are true
     */
    public static ConditionExpression and(ConditionExpression... subexpressions) {
        return join(") AND (", Arrays.asList(subexpressions));
    }

    /**
     * Combine multiple subexpressions with a logical and operation
     * @param subexpressions The subexpressions to join
     * @return A single expression that will evaluate to true if all subexpressions are true
     */
    public static ConditionExpression and(Collection<ConditionExpression> subexpressions) {
        return join(") AND (", subexpressions);
    }

    /**
     * Join multiple condition expressions together with an operator
     * @param operator The operator, with a closing paren on the left and an opening paren on the right
     * @param subexpressions The subexpressions to join
     * @return A condition expression that is the result of joining these together
     */
    private static ConditionExpression join(String operator, Collection<ConditionExpression> subexpressions) {
        StringBuilder sb = new StringBuilder();
        Map<String, AttributeValue> values = null;
        Map<String, String> attributeNames = null;
        for (var expr: subexpressions) {
            sb.append(sb.length() == 0 ? "(" : operator);
            sb.append(expr.getExpression());
            var subValues = expr.getValues();
            if (subValues != null) {
                if (values == null) {
                    values = new HashMap<>(subValues);
                } else {
                    values.putAll(subValues);
                }
            }
            var subAttrs = expr.getAttributeNames();
            if (subAttrs != null) {
                if (attributeNames == null) {
                    attributeNames = new HashMap<>(subAttrs);
                } else {
                    attributeNames.putAll(subAttrs);
                }
            }
        }
        return new ConditionExpression(sb.append(')').toString(), values, attributeNames);
    }

    /**
     * Build an expression that requires an item with matching keys to be already present. This is useful in putExtended operations,
     * to ensure that you modify but do not create a new item
     * @param table The table that this expression will work on
     * @return An expression that will fail if there is no item with matching keys
     */
    public static ConditionExpression requirePresent(DynamoTable<?, ?, ?> table) {
        return requirePresent(table.getPartitionKeyAttributes().get(0));
    }

    /**
     * Build an expression that requires an item with matching keys and the specified attribute to be already present.
     * @param attributeName The attribute that must be present on the specified item
     * @return An expression that will fail if there is no item with matching keys or if the matching item does not have
     *         the given attribute
     */
    public static ConditionExpression requirePresent(String attributeName) {
        return new ConditionExpression("attribute_exists(#" + attributeName + ")",
            null,
            Map.of("#" + attributeName, attributeName));
    }

    /**
     * Build an expression that fails if an item with matching keys is already present. This is useful in putExtended operations,
     * to ensure that you create a new item but do not overwrite an existing one
     * @param table The table that this expression will work on
     * @return An expression that will fail if there is an item with matching keys
     */
    public static ConditionExpression requireAbsent(DynamoTable<?, ?, ?> table) {
        return requireAbsent(table.getPartitionKeyAttributes().get(0));
    }

    /**
     * Build an expression that fails if there is an item with matching keys and the specified attribute.
     * @param attributeName The attribute that must not be present
     * @return An expression that will fail if there is an item with matching keys and the given attribute
     */
    public static ConditionExpression requireAbsent(String attributeName) {
        return new ConditionExpression("attribute_not_exists(#" + attributeName + ")",
            null,
            Map.of("#" + attributeName, attributeName));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + expression + ", " + values + ", " + attributeNames + "]";
    }
}
