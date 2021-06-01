package ai.phast.ctdynamo.processor;

import javax.lang.model.element.Element;

/**
 * Metadata for an index
 */
class IndexMetadata {

    /** The name of the partition attribute */
    private String partitonAttribute;

    /** The name of the sort attribute */
    private String sortAttribute;

    /** The element that triggered the creation of this index */
    private Element declaringElement;

    /**
     * Sets the partition attribute of this index
     *
     * @param value   The attribute name
     * @param element The element that gives us the second attribute (used only for error reporting)
     * @throws CtException If the attribute is already set to another value
     */
    public void setPartitonAttribute(String value, Element element) throws CtException {
        if ((partitonAttribute != null) && !partitonAttribute.equals(value)) {
            throw new CtException("Secondary partition attribute set twice: " + partitonAttribute + " and " + value, element);
        }
        partitonAttribute = value;
        declaringElement = element;
    }

    /**
     * Get the partition attribute name
     *
     * @return The partition attribute name
     */
    public String getPartitonAttribute() {
        return partitonAttribute;
    }

    /**
     * Sets the sort attribute of this index
     *
     * @param value   The attribute name
     * @param element The element that gives us the second attribute (used only for error reporting)
     * @throws CtException If the attribute is already set to another value
     */
    public void setSortAttribute(String value, Element element) throws CtException {
        if ((sortAttribute != null) && !sortAttribute.equals(value)) {
            throw new CtException(("Secondary sort attribute set twice: " + partitonAttribute + " and " + value));
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
}
