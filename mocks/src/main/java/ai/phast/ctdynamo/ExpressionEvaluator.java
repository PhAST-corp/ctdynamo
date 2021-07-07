package ai.phast.ctdynamo;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a dynamo expression
 */
public class ExpressionEvaluator extends DynamoBaseListener {

    /** The attributes specified in a map */
    private final Map<String, String> attributes;

    /** The values passed in */
    private final Map<String, AttributeValue> values;

    /** The preexisting item */
    private final Map<String, AttributeValue> curItem;

    /**
     * Build an evaluator
     * @param attributes The attributes
     * @param values The values
     * @param curItem The item
     */
    private ExpressionEvaluator(Map<String, AttributeValue> curItem, Map<String, String> attributes, Map<String, AttributeValue> values) {
        this.attributes = attributes;
        this.values = values;
        this.curItem = curItem;
    }

    /**
     * Evaluate an expression
     * @param expression The expression
     * @param curItem The item in the database that we are evaluating
     * @return The value of the expression
     */
    public static AttributeValue eval(ConditionExpression expression, Map<String, AttributeValue> curItem) {
        var parser = new DynamoParser(new CommonTokenStream(new DynamoLexer(CharStreams.fromString(expression.getExpression()))));
        parser.addErrorListener(new ErrorListener());
        parser.addParseListener(new ExpressionEvaluator(curItem, expression.getAttributeNames(), expression.getValues()));
        return parser.expression().value;
    }

    /**
     * Evaluate, and return the boolean value.
     * @param expression The expression to evaluate
     * @param curItem The item in the database
     * @return The boolean result of the expression
     * @throws RuntimeException If the expression does not evaluate as a boolean
     */
    public static boolean evalBool(ConditionExpression expression, Map<String, AttributeValue> curItem) {
        var result = eval(expression, curItem);
        if (result.bool() == null) {
            throw new RuntimeException("Evaluation of " + expression + " is not a bool, got: " + result);
        }
        return result.bool();
    }

    /**
     * Evaluate an update operation. The current item is copied and returned after modification.
     * @param update The expression
     * @param curItem The current item or an empty map if there is none
     * @param values The values passed in
     * @param names The name map
     * @return The updated item
     */
    public static Map<String, AttributeValue> update(String update, Map<String, AttributeValue> curItem,
                                                     Map<String, AttributeValue> values, Map<String, String> names) {
        var result = new HashMap<>(curItem);
        var parser = new DynamoParser(new CommonTokenStream(new DynamoLexer(CharStreams.fromString(update))));
        parser.addErrorListener(new ErrorListener());
        parser.addParseListener(new ExpressionEvaluator(result, names, values));
        parser.update();
        return result;
    }

    /**
     * Evaluate an attribute reference atom, something like "#partition_key". We look up the value of the attribute
     * that is referenced and store it in the result of the parse element.
     * @param ctx The context of the parse element
     */
    @Override
    public void exitAttributeRefAtom(DynamoParser.AttributeRefAtomContext ctx) {
        var text = ctx.getText();
        var attr = attributes.get(text);
        if (attr == null) {
            throw new RuntimeException("Unknown attribute reference " + text);
        }
        ctx.value = curItem.get(attr);
    }

    /**
     * Evaluate a value reference atom, something like ":name". We look up the value
     * and store it in the result of the parse element.
     * @param ctx The context of the parse element
     */
    @Override
    public void exitValueRefAtom(DynamoParser.ValueRefAtomContext ctx) {
        var valueId = ctx.getText();
        ctx.value = values.get(valueId);
        if (ctx.value == null) {
            throw new IllegalArgumentException("No value labelled " + valueId + " is present");
        }
    }

    /**
     * Evaluate an attribute atom, something like "partition_key" (note no "#" so the attribute name is provided directly).
     * We get the attribute value and store it in the result of the parse element.
     * @param ctx The context of the parse element
     */
    @Override
    public void exitAttributeAtom(DynamoParser.AttributeAtomContext ctx) {
        ctx.value = curItem.get(ctx.getText());
    }

    @Override
    public void exitSetTerm(DynamoParser.SetTermContext ctx) {
        curItem.put(ctx.lvalAtom().value, ctx.andOr().value);
    }

    /**
     * Evaluate a removal. This should be just an attribute name or attribute reference.
     * @param ctx Context
     */
    @Override
    public void exitRemoveTerms(DynamoParser.RemoveTermsContext ctx) {
        curItem.remove(ctx.lvalAtom().value);
    }

    @Override
    public void exitLvalAtom(DynamoParser.LvalAtomContext ctx) {
        var text = ctx.getText();
        if (ctx.ATTRIBUTE() == null) {
            // Must be ATTRIBUTE_REF
            ctx.value = Objects.requireNonNull(attributes.get(text), "Unknown attribute reference: " + text);
        } else {
            ctx.value = text;
        }
    }

    /**
     * Implement dynamo's attribute_exists() function
     * @param valueIn The input value
     * @return true or false based on valueIn existing
     */
    static AttributeValue attributeExists(AttributeValue valueIn) {
        return makeBool(valueIn != null);
    }

    /**
     * Implement dynamo's attribute_exists() function
     * @param valueIn The input value
     * @return true or false based on valueIn existing
     */
    static AttributeValue attributeNotExists(AttributeValue valueIn) {
        return makeBool(valueIn == null);
    }

    /**
     * Add two attribute values. Currently only numbers are supported
     * @param left The left value
     * @param right The right value
     * @return The sum of the values
     */
    static AttributeValue add(AttributeValue left, AttributeValue right) {
        var leftStr = left.n();
        var rightStr = right.n();
        if ((leftStr == null) || (rightStr == null)) {
            throw new RuntimeException("Cannot compute: " + left + " + " + right);
        }
        if (leftStr.indexOf('.') >= 0 || rightStr.indexOf('.') >= 0 || leftStr.indexOf('e') >= 0 || rightStr.indexOf('e') >= 0) {
            // Double precision
            return AttributeValue.builder().n(Double.toString(Double.parseDouble(leftStr) + Double.parseDouble(rightStr))).build();
        } else {
            return AttributeValue.builder().n(Long.toString(Long.parseLong(leftStr) + Long.parseLong(rightStr))).build();
        }
    }

    /**
     * Compute the dynamo list_append function
     * @param list1 The first list
     * @param list2 The second list
     * @return A new attribute value that appends the other two
     * @throws RuntimeException If either attribute value is a non-list
     */
    static AttributeValue listAppend(AttributeValue list1, AttributeValue list2) {
        if (!list1.hasL()) {
            throw new RuntimeException("No list in first value: " + list1);
        }
        if (!list2.hasL()) {
            throw new RuntimeException("No list in second value: " + list2);
        }
        var result = new ArrayList<>(list1.l());
        result.addAll(list2.l());
        return AttributeValue.builder().l(result).build();
    }

    /**
     * Turn a boolean into an AttributeValue
     * @param value The boolean
     * @return The AttributeValue
     */
    static AttributeValue makeBool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    /**
     * Unpack a boolean attribute value
     * @param value The attribute value
     * @return The boolean component
     * @throws RuntimeException If the attribute value is not a boolean
     */
    static boolean unpackBool(AttributeValue value) {
        var bool = value.bool();
        if (bool == null) {
            throw new RuntimeException("Expected a boolean AttributeValue, got: " + value);
        }
        return bool;
    }

    /**
     * Indicate that something is not yet implemented.
     * @param operation The operation that was found in the dynamo expression
     * @throws UnsupportedOperationException Always
     */
    static void unsupported(String operation) {
        throw new UnsupportedOperationException("Not implemented yet: \"" + operation + "\"");
    }

    /**
     * Basic antlr error listener that throws exceptions on syntax errors.
     */
    private static class ErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new RuntimeException("Syntax error: " + msg);
        }
    }
}