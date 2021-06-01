package ai.phast.ctdynamo.processor;

import javax.lang.model.element.Element;

/**
 * A compile time exception that has a message to be shown to the user. It may have an element pointing to where in the code
 * this exception was generated.
 */
public class CtException extends Exception {

    /** The element that triggered this exception */
    private final Element element;

    /**
     * Create an exception with no triggering element
     * @param message The message to show to the user
     */
    public CtException(String message) {
        this(message, null);
    }

    /**
     * Create an exception with a triggering element
     * @param message The message to show to the user
     * @param sourceElement The element that caused this exception
     */
    public CtException(String message, Element sourceElement) {
        super(message);
        element = sourceElement;
    }

    /**
     * Get the element that caused this exception
     * @return The element that caused this exception, or null if we don't know the exact element
     */
    public Element getElement() {
        return element;
    }
}
