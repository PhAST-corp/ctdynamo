package ai.phast.ctdynamo.processor;

import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;

/**
 * Metadata for an index
 */
@ToString
class IndexMetadata {

    /** The largest number of partition keys DynamoDB will accept on an index */
    static final int MAX_PARTITION_KEYS = 4;

    /**
     * The names of the partition attributes, indexed by their zero-based position. Entries are null until the
     * matching order is seen, so this may be sparse until {@link #validate(String)} confirms otherwise.
     */
    private final String[] partitionAttributes = new String[MAX_PARTITION_KEYS];

    /** The name of the sort attribute */
    private String sortAttribute;

    /** The element that triggered the creation of this index */
    private Element declaringElement;

    /**
     * Sets one of the partition attributes of this index
     *
     * @param value   The attribute name
     * @param order   The 1-based position of this partition key, from the annotation
     * @param element The element carrying the annotation (used only for error reporting)
     * @throws CtException If the order is out of range, or that position is already set to another value
     */
    public void setPartitionAttribute(String value, int order, Element element) throws CtException {
        if (order < 1 || order > MAX_PARTITION_KEYS) {
            throw new CtException("Partition key order must be between 1 and " + MAX_PARTITION_KEYS
                + ", got " + order, element);
        }
        var existing = partitionAttributes[order - 1];
        if ((existing != null) && !existing.equals(value)) {
            throw new CtException("Secondary partition attribute " + order + " set twice: "
                + existing + " and " + value, element);
        }
        partitionAttributes[order - 1] = value;
        declaringElement = element;
    }

    /**
     * Get the partition attribute names, in the order the customer assigned them
     *
     * @return The partition attribute names. Only valid after {@link #validate(String)} has passed, which is what
     *         guarantees the list is dense
     */
    public List<String> getPartitionAttributes() {
        var result = new ArrayList<String>(MAX_PARTITION_KEYS);
        for (var attribute : partitionAttributes) {
            if (attribute != null) {
                result.add(attribute);
            }
        }
        return result;
    }

    /**
     * Get one partition attribute name by its zero-based position
     *
     * @param position The zero-based position
     * @return The attribute name, or null if this index has no partition key at that position
     */
    public String getPartitionAttribute(int position) {
        return position < MAX_PARTITION_KEYS ? partitionAttributes[position] : null;
    }

    /**
     * Sets the sort attribute of this index
     *
     * @param value   The attribute name
     * @param element The element carrying the annotation (used only for error reporting)
     * @throws CtException If the attribute is already set to another value
     */
    public void setSortAttribute(String value, Element element) throws CtException {
        if ((sortAttribute != null) && !sortAttribute.equals(value)) {
            throw new CtException(("Secondary sort attribute set twice: " + sortAttribute + " and " + value));
        }
        declaringElement = element;
        sortAttribute = value;
    }

    /**
     * Get the sort attribute name
     *
     * @return The sort attribute name
     */
    public String getSortAttribute() {
        return sortAttribute;
    }

    /**
     * Get the element that triggered creation of this index
     * @return The element that triggered creation of this index
     */
    public Element getDeclaringElement() {
        return declaringElement;
    }

    /**
     * Ensure that the index can be used. The partition orders must start at 1 and have no gaps, because the runtime
     * passes them positionally and treats a null partition value as "this index has fewer keys". An attribute may
     * not be both a partition key and the sort key of the same index.
     * @param name The name of the index
     * @throws CtException If the index is not acceptable
     */
    public void validate(String name) throws CtException {
        if (partitionAttributes[0] == null) {
            throw new CtException("No partition key for secondary index " + name
                + (getPartitionAttributes().isEmpty()
                   ? ""
                   : ". Partition key orders must start at 1"), declaringElement);
        }
        var seenGap = false;
        for (var i = 0; i < MAX_PARTITION_KEYS; i++) {
            if (partitionAttributes[i] == null) {
                seenGap = true;
            } else if (seenGap) {
                throw new CtException("Partition key orders for secondary index " + name
                    + " must have no gaps, but found " + partitionAttributes[i] + " at order " + (i + 1)
                    + " with an earlier order missing", declaringElement);
            }
        }
        var duplicates = getPartitionAttributes().stream()
            .filter(attribute -> attribute.equals(sortAttribute))
            .collect(Collectors.toList());
        if (!duplicates.isEmpty()) {
            throw new CtException("Attribute(s) " + String.join(", ", duplicates) + " are both a partition key and"
                + " the sort key of secondary index " + name, declaringElement);
        }
    }
}
