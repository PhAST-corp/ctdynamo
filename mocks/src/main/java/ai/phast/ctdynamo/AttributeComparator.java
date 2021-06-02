package ai.phast.ctdynamo;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Comparator;
import java.util.Objects;

/**
 * Comparator that compares two AttributeValue objects. They must be both string, or both numeric.
 */
class AttributeComparator implements Comparator<AttributeValue> {

    /** The singleton instance of this class */
    public static final AttributeComparator INSTANCE = new AttributeComparator();

    /** A "magic" AttributeValue that is guaranteed to always be less than all other attribute values */
    public static final AttributeValue LOWEST_AV = AttributeValue.builder().b(SdkBytes.fromByteArray(new byte[0])).build();

    /** A "magic" AttributeValue that is guaranteed to always be greater than all other attribute values */
    public static final AttributeValue HIGHEST_AV = AttributeValue.builder().b(SdkBytes.fromByteArray(new byte[1])).build();

    /**
     * Private constructor. Singleton.
     */
    private AttributeComparator() {
    }

    @Override
    public int compare(AttributeValue value1, AttributeValue value2) {
        // See if one of the values is a placeholder "call this the lowest/highest" value. Note that the placeholders
        // are unique objects, so we use == instead of equals.
        if (value1 == LOWEST_AV) {
            return value2 == LOWEST_AV ? 0 : -1;
        } else if (value1 == HIGHEST_AV) {
            return value2 == HIGHEST_AV ? 0 : 1;
        }
        if (value2 == LOWEST_AV) {
            return 1;
        } else if (value2 == HIGHEST_AV) {
            return -1;
        }

        var text1 = value1.s();
        if (text1 != null) {
            // It's a text object. Easy enough, string compary vs. the other attribute value
            return text1.compareTo(Objects.requireNonNull(value2.s(), "Cannot compare: " + value1 + " vs. " + value2));
        }

        text1 = value1.n();
        if (text1 != null) {
            // It's numeric. Get the other value's number string, then work out whether we shoud use integer or floating point comparison
            var text2 = Objects.requireNonNull(value2.n(), "Cannot compare: " + value1 + " vs. " + value2);
            var isDecimal = text1.indexOf('.') >= 0 || text2.indexOf('.') >= 0
                                || text1.indexOf('e') >= 0 || text2.indexOf('e') >= 0
                                || text1.indexOf('E') >= 0 || text2.indexOf('E') >= 0;
            if (isDecimal) {
                return Double.compare(Double.parseDouble(text1), Double.parseDouble(text2));
            } else {
                return Long.compare(Long.parseLong(text1), Long.parseLong(text2));
            }
        }
        throw new IllegalArgumentException("Cannot compare: " + value1 + " vs. " + value2);
    }
}
