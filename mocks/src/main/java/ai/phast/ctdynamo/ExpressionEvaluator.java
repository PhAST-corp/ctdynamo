package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parse and evaluate dynamo expressions. This is a really, really bad parser; it appects a lot of gibberish that would
 * be considered syntax errors in actual Dynamo, its lexer is horrendously slow, it may be operator precedence wrong, and
 * some data types it doesn't handle very well. But it does surprisingly well for many expressions and it only took a couple
 * hours to code up, so it's not a total disaster.
 *
 * <p>Eventually writing a "real" parser may become necessary. Antlr or something similar would probably make it pretty easy.
 */
public class ExpressionEvaluator {

    /**
     * All token types we recognize. Each has a regex which matches it, the number of operands that it expects, and its
     * operator precedence.
     */
    enum TokenType {
        /** Start of a parenthesized expression */
        OPEN_PAREN("\\("),

        /** End of a parenthesized expression */
        CLOSE_PAREN("\\)"),

        /** Basic binary operator */
        AND("AND", 2, 20),

        /** Basic binary operator */
        OR("OR", 2, 20),

        /** Basic binary operator */
        NOT("NOT", 1, 50),

        /** Substring/subset testing */
        IN("IN", 2, 21),

        /** a &lt;= x &lt;= y range operator. This fails to execute, if we ever call this it means there is no following "and" */
        BETWEEN("BETWEEN", 3, 21),

        /** Between after it is merged with an and */
        BETWEEN_WITH_AND("// Do not match //", 3, 21),

        /** Comparison operator */
        NOT_EQ("<>", 2, 22),

        /** Range operator */
        LE("<=", 2, 22),

        /** Range operator */
        LT("<", 2, 22),

        /** Range operator */
        GE(">=", 2, 22),

        /** Range operator */
        GT(">", 2, 22),

        /** Comparison operator */
        EQ("=", 2, 22),

        /** Function */
        EXISTS("attribute_exists", 1, 51),

        /** Function */
        NOT_EXISTS("attribute_not_exists", 1, 51),

        /** Function */
        TYPE("attribute_type", 1, 51),

        /** Function */
        BEGINS_WITH("begins_with", 1, 51),

        /** Function */
        CONTAINS("contains", 2, 40), SIZE("size", 1, 50),

        /** Any attribute reference */
        ATTRIBUTE_REF("#[a-zA-Z_][a-zA-Z0-9_]*"),

        /**
         * An attribute directly inserted into the expression. This will match function names also so we need to check it after
         * we check all functions
         */
        ATTRIBUTE("[a-zA-Z_][a-zA-Z0-9_]*"),

        /** Any value reference */
        VALUE_REF(":[a-zA-Z_][a-zA-Z0-9_]*"),

        /** End of file marker */
        EOF(null);

        /** The pattern that matches this token type if the token is at the start of a string */
        public final Pattern pattern;

        /** How many operands this token expects (0 if this is not an operator or function) */
        public final int numOperands;

        /** The precedence of this operator/function (-1 if this is not an operator or function) */
        public final int precedence;

        /**
         * Build a token type that is not an operator or function
         * @param match The regex pattern that matches this token type
         */
        TokenType(String match) {
            this(match, 0, -1);
        }

        /**
         * Build a token type
         * @param match The regex that matches this token type
         * @param numOperands How many operands this token expects (0 if this is not an operator or function)
         * @param precedence The precedence of this operator/function (-1 if this is not an operator or function)
         */
        TokenType(String match, int numOperands, int precedence) {
            //noinspection RegExpUnexpectedAnchor
            pattern = Pattern.compile(match == null ? "(\\p{Space}*)\\z" : "\\p{Space}*(" + match + ")(.*)");
            this.numOperands = numOperands;
            this.precedence = precedence;
        }
    }

    /** Our stack of operators */
    private final ArrayDeque<Token> operators = new ArrayDeque<>();

    /** Our stack of atoms (atoms are attribute references, attributes, or value references) */
    private final ArrayDeque<Token> atoms = new ArrayDeque<>();

    /** A map from string to attribute name */
    private final Map<String, String> attributes;

    /** A map from string to value */
    private final Map<String, AttributeValue> values;

    /** The text of the expression we are evaluating */
    private final String text;

    /**
     * Build an expression evaluator for the given expression data
     * @param text The text of the expression
     * @param attributes A map from string to attribute name
     * @param values A map from string to value
     */
    ExpressionEvaluator(String text, Map<String, String> attributes, Map<String, AttributeValue> values) {
        this.text = text;
        this.attributes = attributes;
        this.values = values == null ? new HashMap<>() : new HashMap<>(values);  // We modify this, so we should copy first
    }

    /**
     * Evaluate a boolean expression
     * @param item The item to compare with the expression
     * @return The result of the expression
     */
    public boolean evalBool(Map<String, AttributeValue> item) {
        return eval(item).bool();
    }

    /**
     * Evaluate an expression
     * @param item The item to compare with the expression
     * @return An attribute value that is the end result of the expression
     * @throws RuntimeException If there is an error parsing or evaluating the expression
     */
    public AttributeValue eval(Map<String, AttributeValue> item) {
        var lastToken = new Token[1];
        operators.clear();
        atoms.clear();
        try {
            // evaluate will reduce us down to a single atom in our atom stack. We evaluate that, and we are done
            return evaluateAtom(evaluate(item, lastToken), item);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing: " + text + ", last token: " + lastToken[0], ex);
        }
    }

    /**
     * Evaluate an expression
     * @param item The item to evaluate against
     * @param lastToken Each token is stored here as we evaluate, so if we throw an exception, we can print the most
     *                  recent token as part of the exception message
     * @return The token that is the last atom that the expression becomes
     * @throws RuntimeException On any error. May be invalid expression syntax, or something unimplemented/broken
     *         in this expression evaluator
     */
    private Token evaluate(Map<String, AttributeValue> item, Token[] lastToken) {
        var tail = text;
        while (true) {
            var token = new Token(tail);
            lastToken[0] = token;
            tail = token.tail;
            switch(token.type) {
                case EOF:
                    // End of expression. Evaluate the operators until there are none left. Then make sure we have
                    // only one atom in our stack, and return that
                    while (!operators.isEmpty()) {
                        evalOperator(operators.pop(), item);
                    }
                    if (atoms.size() != 1) {
                        throw new RuntimeException("Error parsing dynamo, multiple values at end: " + text);
                    }
                    return atoms.pop();
                case OPEN_PAREN:
                    // Open paren. Push it on both stacks (operators and atoms) as a placeholder, so we know how far
                    // back to go when we get the close paren
                    operators.push(token);
                    atoms.push(token);
                    break;
                case CLOSE_PAREN:
                    // Close paren. Evaluate operators as if we hit EOF until we find the open paren. Pop that. We
                    // should now have in our atom stack a single atom then an open paren; remove them both, replace
                    // only the open paren, and done!
                    while (true) {
                        var op = operators.pop();
                        if (op.type == TokenType.OPEN_PAREN) {
                            // Completed all operators inside our parens
                            var result = atoms.pop();
                            if (atoms.pop().type != TokenType.OPEN_PAREN) {
                                throw new RuntimeException("Mismatched paren: " + text);
                            }
                            atoms.push(result);
                            break;
                        } else {
                            evalOperator(op, item);
                        }
                    }
                    break;
                case ATTRIBUTE:
                case ATTRIBUTE_REF:
                case VALUE_REF:
                    // Found an atom token. Push it to the atom stack
                    atoms.push(token);
                    break;
                default:
                    if (token.type == TokenType.AND && !operators.isEmpty() && operators.peek().type == TokenType.BETWEEN) {
                        // Combine BETWEEN and AND into a new operator, return that
                        operators.pop();
                        token = new Token(TokenType.BETWEEN_WITH_AND, "BETWEEN ... AND", token.tail);
                    }
                    // Most be an operator. If it is lower precedence than the previous operator, then we need to
                    // evaluate the previous operator before pushing this on onto the stack. Keep evaluating operators
                    // until an even lower priority one is found, or until we clear out our stack
                    if (token.type.precedence < 0) {
                        // We somehow found an atom when expecting an operator
                        throw new RuntimeException("Error parsing: " + text + ", found token " + token);
                    }
                    while (!operators.isEmpty() && operators.peek().type.precedence >= token.type.precedence) {
                        evalOperator(operators.pop(), item);
                    }
                    operators.push(token);
            }
        }
    }

    /**
     * Evaluate an operator. We will create a new value (starting with :: and ending with a counter so we know it is
     * unique), push that as a value with the result of the operator
     * @param operator The operator token
     * @param item The item we are evaluating the operator against
     */
    private void evalOperator(Token operator, Map<String, AttributeValue> item) {
        var value = "::" + (values.size() + 1);
        values.put(value, executeOperator(operator, item));
        atoms.push(new Token(TokenType.VALUE_REF, value, null));
    }

    /**
     * Execute an operator. Pop operands as needed then compute the result and return it
     * @param operator The operator to execute
     * @param item The item we are evaluating against
     * @return The result of the operator
     * @throws RuntimeException If we haven't yet written code for this operator
     */
    private AttributeValue executeOperator(Token operator, Map<String, AttributeValue> item) {
        if (operator.type.numOperands == 0) {
            throw new RuntimeException("Can't eval " + operator.type);
        }
        var atom1 = atoms.pop();  // Saved for easier debugging
        AttributeValue val1;
        AttributeValue val2 = null;
        AttributeValue val3 = null;
        switch(operator.type.numOperands) {
            case 1:
                val1 = evaluateAtom(atom1, item);
                break;
            case 2:
                val2 = evaluateAtom(atom1, item);
                val1 = evaluateAtom(atoms.pop(), item);
                break;
            case 3:
                val3 = evaluateAtom(atom1, item);
                val2 = evaluateAtom(atoms.pop(), item);
                val1 = evaluateAtom(atoms.pop(), item);
                break;
            default:
                throw new RuntimeException();
        }
        switch(operator.type) {
            case LT:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) < 0);
            case LE:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) <= 0);
            case GT:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) > 0);
            case GE:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) >= 0);
            case EQ:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) == 0);
            case NOT_EQ:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) != 0);
            case BETWEEN:
                throw new RuntimeException("Cannot process BETWEEN without AND");
            case BETWEEN_WITH_AND:
                return makeBool(AttributeComparator.INSTANCE.compare(val1, val2) >= 0 && AttributeComparator.INSTANCE.compare(val3, val1) >= 0);
            case AND:
                //noinspection ConstantConditions
                return makeBool(val1.bool() && val2.bool());
            case OR:
                //noinspection ConstantConditions
                return makeBool(val1.bool() || val2.bool());
            case NOT:
                return makeBool(!val1.bool());
            case EXISTS:
                return makeBool(val1 != null);
            case NOT_EXISTS:
                return makeBool(val1 == null);
            default:
                throw new RuntimeException("Unknown operator: " + operator.type);
        }
    }

    /**
     * Turn a boolean into an attribute value boolean
     * @param value The value to wrap in an attribute value
     * @return The attribute value
     */
    private AttributeValue makeBool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    /**
     * Evaluate an atom, turning it into an AttributeValue
     * @param token The token to evaluate
     * @param item The item we use as input
     * @return The attribute value result of the atom
     * @throws RuntimeException If we get here and token is somehow not an atom
     */
    private AttributeValue evaluateAtom(Token token, Map<String, AttributeValue> item) {
        switch(token.type) {
            case ATTRIBUTE_REF:
                return item.get(attributes.get(token.value));
            case ATTRIBUTE:
                return item.get(token.value);
            case VALUE_REF:
                return values.get(token.value);
            default:
                throw new RuntimeException("Not an atom: " + token);
        }
    }

    /**
     * A single token of our expression
     */
    private static class Token {

        /** The type of this token */
        final TokenType type;

        /** The text that makes up the body of the token */
        final String value;

        /** All text in the expression that comes after this token */
        final String tail;

        /**
         * Build a token
         * @param type The type
         * @param value The text of the token
         * @param tail The rest of the expression
         */
        Token(TokenType type, String value, String tail) {
            this.type = type;
            this.value = value;
            this.tail = tail;
        }

        /**
         * Build a token from the current expression text
         * @param text The expression text left (after any earlier tokens are removed)
         * @throws RuntimeException If the text doesn't match any token type
         */
        Token(String text) {
            // Loop through all token types until one matches the text. That's what we are.
            for (var tt: TokenType.values()) {
                var match = tt.pattern.matcher(text);
                if (match.matches()) {
                    type = tt;
                    value = match.group(1);
                    tail = (tt == TokenType.EOF ? null : match.group(2));
                    return;
                }
            }
            throw new RuntimeException("Unrecognized Dynamo expression: " + text);
        }

        @Override
        public String toString() {
            return "Token[" + type + ", val=" + value + "]";
        }
    }
}
