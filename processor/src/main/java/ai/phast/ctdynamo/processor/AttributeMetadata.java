package ai.phast.ctdynamo.processor;

import com.squareup.javapoet.TypeName;
import lombok.Getter;

import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Tracks the metadata on a single attribute
 */
class AttributeMetadata {

    /** The name of the attribute */
    public final String name;

    /** The return type of the getter */
    public final TypeMirror returnType;

    /** The return type of the getter coerced into a class */
    public final DeclaredType boxedReturnType;

    /** The codec to use for this attribute, or null if we use a default codec */
    public final TypeName codecClass;

    /**
     * The element that we should blame for any errors this attribute causes. This is the thing annotated with
     * a Dynamo annotation, or the getter if this is from a default annotation.
     */
    public final Element element;

    /** The name of the getter, usually "getAttributeName" or "isAttributeName" */
    @Getter
    private String getterName;

    /** The name of the setter, usually "setAttributeName" */
    @Getter
    private String setterName;

    /**
     * Build an attribute metadata
     * @param name The name of the attribute
     * @param returnType       The return type of the getter
     * @param boxedReturnType  The boxed return type
     * @param codecClass       The codec to use on this attribute, or null if none is specified
     * @param element The element to indicate when we have errors related to this attribute
     */
    AttributeMetadata(String name, TypeMirror returnType, DeclaredType boxedReturnType, TypeName codecClass,
                      Element element) {
        this.name = Objects.requireNonNull(name);
        this.returnType = returnType;
        this.boxedReturnType = boxedReturnType;
        this.codecClass = codecClass;
        this.element = element;
    }

    /**
     * Attribute metadata for an attribute marked with "@DynamoIgnore"
     * @param element The element to indicate as the cause of any errors related to this attribute
     */
    AttributeMetadata(Element element) {
        name = null;
        returnType = null;
        boxedReturnType = null;
        codecClass = null;
        this.element = element;
    }

    /**
     * Set the name of our setter
     * @param method The method that may be our setter
     * @param typeTools For type comparison
     * @throws CtException If we already have a setter
     */
    public void setSetterName(ExecutableElement method, TypeTools typeTools) throws CtException {
        if (isIgnored()) {
            return;
        }
        var params = method.getParameters();
        if ((params.size() == 1) && typeTools.equal(params.get(0).asType(), returnType)) {
            var methodName = method.getSimpleName().toString();
            if (setterName == null) {
                setterName = methodName;
            } else if (!setterName.equals(methodName)) {
                throw new CtException("Multiple setters found for attribute " + name, element);
            }
        }
    }

    /**
     * Set the name of our getter if the types match
     * @param method The method that might be our getter
     * @param typeTools For type comparison
     * @throws CtException If we already have a getter
     */
    public void setGetterName(ExecutableElement method, TypeTools typeTools) throws CtException {
        if (isIgnored()) {
            return;
        }
        if (typeTools.equal(method.getReturnType(), returnType)) {
            var methodName = method.getSimpleName().toString();
            if (getterName == null) {
                getterName = methodName;
            } else if (!getterName.equals(methodName)) {
                throw new CtException("Multiple getters found for attribute " + name, method);
            }
        }
    }

    /**
     * Ensure that this attribute has a setter and a getter
     * @throws CtException If we are missing a setter or getter
     */
    public void validate() throws CtException {
        if (!isIgnored()) {
            if (getterName == null) {
                throw new CtException("No getter found for attribute " + name, element);
            }
            if (setterName == null) {
                throw new CtException("No setter found for attribute " + name, element);
            }
        }
    }

    /**
     * True if this attribute is marked as ignore
     *
     * @return true if this attribute is marked as ignore
     */
    public boolean isIgnored() {
        return name == null;
    }
}
