package ai.phast.ctdynamo.processor;

import ai.phast.ctdynamo.DynamoIndex;
import ai.phast.ctdynamo.annotations.DefaultCodec;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A class of immutable member variables. These are values that require the annotation processing environment, so they
 * can't be done statically, but are used often enough that it is worth computing them once and keeping them.
 *
 * <p>A few useful utitily functions/conversions are included as well
 */
public class TypeTools {

    /** Mirror type of Map&lt;String, AttributeValue> */
    public final TypeMirror dynamoMapMirror;

    /** Mirror type of {@link DefaultCodec} */
    public final TypeMirror defaultCodecMirror;

    /** Mirror type of String */
    public final TypeMirror stringMirror;

    /** Mirror type of List&lt;?> */
    public final TypeMirror listMirror;

    /** Mirror of type Map&lt;?, ?> */
    public final TypeMirror mapMirror;

    /** Mirror type of Set&lt;?> */
    public final TypeMirror setMirror;

    /** Mirror type of Enum&lt;?> */
    public final TypeMirror enumMirror;

    /** Mirror type of Number */
    public final TypeMirror numberMirror;

    /** Mirror type for the boxed class Boolean */
    public final TypeMirror booleanMirror;

    /** Mirror type for the Void class */
    public final TypeMirror voidMirror;

    /** Mirror type for the Object class */
    public final TypeMirror objectMirror;

    /** Mirror type for the DynamoIndex class */
    public final TypeMirror indexMirror;

    /** Type utilities */
    public final Types types;

    /** Element utitilies */
    public final Elements elements;

    /** Maps a type kind to the boxed version of that type kind */
    private final Map<TypeKind, DeclaredType> typeKindToBoxedType;

    /**
     * Construct our type tools
     * @param env Our processing enironment
     */
    public TypeTools(ProcessingEnvironment env) {
        types = env.getTypeUtils();
        elements = env.getElementUtils();

        // Determine our mirror objects
        dynamoMapMirror = types.getDeclaredType(elements.getTypeElement(Map.class.getCanonicalName()),
            types.getDeclaredType(elements.getTypeElement(String.class.getCanonicalName())),
            types.getDeclaredType(elements.getTypeElement(AttributeValue.class.getCanonicalName())));
        defaultCodecMirror = types.getDeclaredType(elements.getTypeElement(DefaultCodec.class.getCanonicalName()));
        stringMirror = types.getDeclaredType(elements.getTypeElement(String.class.getCanonicalName()));
        listMirror = types.getDeclaredType(elements.getTypeElement(List.class.getCanonicalName()),
            types.getWildcardType(null, null));
        mapMirror = types.getDeclaredType(elements.getTypeElement(Map.class.getCanonicalName()),
            types.getWildcardType(null, null), types.getWildcardType(null, null));
        setMirror = types.getDeclaredType(elements.getTypeElement(Set.class.getCanonicalName()),
            types.getWildcardType(null, null));
        enumMirror = types.getDeclaredType(elements.getTypeElement(Enum.class.getCanonicalName()),
            types.getWildcardType(null, null));
        numberMirror = types.getDeclaredType(elements.getTypeElement(Number.class.getCanonicalName()));
        booleanMirror = types.getDeclaredType(elements.getTypeElement(Boolean.class.getCanonicalName()));
        voidMirror = types.getDeclaredType(elements.getTypeElement(Void.class.getCanonicalName()));
        objectMirror = types.getDeclaredType(elements.getTypeElement(Object.class.getCanonicalName()));
        indexMirror = types.getDeclaredType(elements.getTypeElement(DynamoIndex.class.getCanonicalName()));
        typeKindToBoxedType = Arrays.stream(TypeKind.values())
                                    .filter(TypeKind::isPrimitive)
                                    .collect(Collectors.toMap(
                                        kind -> kind,
                                        kind -> (DeclaredType)types.boxedClass(types.getPrimitiveType(kind)).asType()));
    }

    /**
     * When an annotation has a parameter that extends Class<?>, you can't read it directly, because the class referred
     * to is in the space of the program being compiled, not yours. The best way to get it seems to be to try to read it,
     * then you get an exception telling you that you tried to use a mirrored type as a class; then you return the
     * mirrored type that the exception refers to.
     * @param supplier A function that reads the class from the annotation
     * @return A TypeMirror that matches the class that we tried to read
     * @throws RuntimeException If we successfully get a class object from the function; this should not be possible
     */
    public TypeMirror getAnnonationClassValue(Supplier<Class<?>> supplier) {
        try {
            var klass = supplier.get();
            throw new RuntimeException("Somehow managed to get class " + klass + " from annotation, that should not be possible");
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
    }

    /**
     * If type is a primitive type, return its boxed equivalent. Otherwise, return it unchanged.
     * @param type The type that may be primitive
     * @return Either type, or the boxed version of type
     */
    public TypeMirror box(TypeMirror type) {
        var result = typeKindToBoxedType.get(type.getKind());
        return result == null ? type : result;
    }

    /**
     * Is this a boxed primitive?
     * @param type The type
     * @return true if this is a boxed primitive type
     */
    public boolean isBoxedPrimitive(TypeMirror type) {
        return typeKindToBoxedType.values().stream().anyMatch(t -> types.isSameType(t, type));
    }

    /**
     * Is this type some kind of mirror? Could be a primitive type (e.g., int) or a boxed type (e.g., Integer)
     * @param type The type to test
     * @return true if type is some kind of number
     */
    public boolean isNumber(TypeMirror type) {
        return types.isSubtype(box(type), numberMirror);
    }

    /**
     * Are these two the same type? Note that TypeMirror.equals is not an accurate way to determine whether types
     * point to the same object.
     * @param type1 The first type
     * @param type2 The second type
     * @return true if these are equivalent
     */
    public boolean equal(TypeMirror type1, TypeMirror type2) {
        return types.isSameType(type1, type2);
    }
}
