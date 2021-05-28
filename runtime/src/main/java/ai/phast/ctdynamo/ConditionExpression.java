package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An expression that returns a boolean (true/false) value. Also adds helpfer functions to build condition expressions
 */
public class ConditionExpression extends Expression {

    /**
     * Build an arbitrary condition expression
     * @param expression The expression string
     * @param values A map from name to value. All values inserted by ctdynamo will have names beginning with "ctdynamo_", so
     *               avoid using names that match
     * @param attributeNames A map from key to attribute name. All attribute names inserted by ctdynamo will have a key equal
     *                       to the attribute name, it is best to stay with that
     */
    public ConditionExpression(String expression, Map<String, AttributeValue> values, Map<String, String> attributeNames) {
        super(expression, values, attributeNames);
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
        return requirePresent(table.getPartitionKeyAttribute());
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
        return requireAbsent(table.getPartitionKeyAttribute());
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
}
