package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Comparator;
import java.util.Map;

/**
 * A comparator that compares two attribute maps but looking at exactly one attribute in each and camparing them
 * with the {@link AttributeComparator}
 */
class AttributeMapComparator implements Comparator<Map<String, AttributeValue>> {

    /** The attribute to compare */
    private final String attributeName;

    /**
     * Constructor
     * @param attributeName The attribute to compare from each map
     */
    AttributeMapComparator(String attributeName) {
        this.attributeName = attributeName;
    }

    @Override
    public int compare(Map<String, AttributeValue> o1, Map<String, AttributeValue> o2) {
        return AttributeComparator.INSTANCE.compare(o1.get(attributeName), o2.get(attributeName));
    }
}
