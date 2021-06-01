package ai.phast.ctdynamo.processor;

import ai.phast.ctdynamo.DynamoCodec;
import ai.phast.ctdynamo.DynamoIndex;
import ai.phast.ctdynamo.DynamoTable;
import ai.phast.ctdynamo.annotations.DynamoAttribute;
import ai.phast.ctdynamo.annotations.DynamoIgnore;
import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondaryPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondarySortKey;
import ai.phast.ctdynamo.annotations.DynamoSortKey;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * A class that, at compile time, processes classes annotation with @DynamoItem. It can generate tables or codecs based
 * on these annotated classes.
 */
public class CtClassGenerator {

    /**
     * Type name used when we need to make a concrete attribute value map
     */
    private static final TypeName ATTRIBUTE_VALUE_HASH_MAP = ParameterizedTypeName.get(HashMap.class, String.class, AttributeValue.class);

    /** The element declaring the type of our table entry */
    private final TypeElement itemType;

    /** The name of the partition key attribute */
    private String partitionKeyAttribute;

    /** The name of the sort key attribute, or null if there is no sort key */
    private String sortKeyAttribute;

    /**
     * A simple counter. Every time a unique ID or name is needed, this is incremented and appended to a letter. It
     * will reset for each class processed but that's OK, the names and IDs only have to be unique within the current
     * class.
     */
    private int paramNumber = 0;

    /** All attributes that are in the class */
    private final Map<String, AttributeMetadata> attributes = new HashMap<>();

    /**
     * A map from getter name to attribute name. This is so that if you annotate the field "value" to be the attribute
     * "cost", then have a "getValue()" method, we'll know that this is actually "getCost".
     */
    private final Map<String, AttributeMetadata> getterTailToAttribute = new HashMap<>();

    /**
     * A mapping from the codec class to codec variable. This is done so that if two or more attributes use the same
     * codec class, we won't make multiple identical codecs.
     */
    private final Map<TypeName, String> codecClassToCodecVar = new HashMap<>();

    /** All indexes for the class */
    private final Map<String, IndexMetadata> indexes = new HashMap<>();

    /** True to ignore null attributes, false to include them in maps written to dynamo */
    private final boolean ignoreNulls;

    /** Useful type-related constants and functions */
    private final TypeTools typeTools;

    /**
     * Create a new class generator
     * @param itemType The type that we need classes for
     * @param typeTools Type-related constants and functions
     * @param ignoreNulls True to ignore null attributes, false to include them in maps written to dynamo
     * @throws CtException If there is an error processing the class
     */
    public CtClassGenerator(TypeElement itemType, TypeTools typeTools, boolean ignoreNulls) throws CtException {
        this.itemType = itemType;
        this.ignoreNulls = ignoreNulls;
        this.typeTools = typeTools;

        // First pass: Process all annotated elements
        for (var element : itemType.getEnclosedElements()) {
            if (hasCtDynamoAnnotation(element)) {
                if (element.getKind() == ElementKind.FIELD) {
                    var field = (VariableElement)element;
                    processAttribute(field, field.getSimpleName().toString(), field.asType());
                } else if (element.getKind() == ElementKind.METHOD) {
                    var exec = (ExecutableElement)element;
                    var getterTail = isGetter(exec);
                    if (getterTail == null) {
                        throw new CtException("Only getters and member variables should have Dynamo attribute annotations", exec);
                    }
                    processAttribute(exec, getterTail, exec.getReturnType());
                } else {
                    throw new CtException("Only getters and member variables should have Dynamo attribute annotations", element);
                }
            }
        }

        // Second pass: Process non-annotated getters whose attributes weren't set up in the first pass
        for (var element: itemType.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD) {
                var exec = (ExecutableElement)element;
                var getterTail = isGetter(exec);
                if ((getterTail != null) && !getterTailToAttribute.containsKey(getterTail)) {
                    processAttribute(exec, getterTail, exec.getReturnType());
                }
            }
        }

        // Third pass: Fill in all our getter/setter names
        for (var element: itemType.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD) {
                var exec = (ExecutableElement)element;
                var tail = isGetter(exec);
                if (tail == null) {
                    // Not a getter. Maybe a setter?
                    tail = isSetter(exec);
                    if (tail != null) {
                        // It's a setter! If we have metadata, then update the setter name
                        var metadata = getterTailToAttribute.get(tail);
                        if (metadata != null) {
                            metadata.setSetterName(exec, typeTools);
                        }
                    }
                } else {
                    // It's a getter! If we have metadata, then update the getter name
                    var metadata = getterTailToAttribute.get(tail);
                    if (metadata != null) {
                        metadata.setGetterName(exec, typeTools);
                    }
                }
            }
        }

        // Now that we have processed all attributes, remove the ignored ones.
        for (var metadata: attributes.values()) {
            metadata.validate();
        }
        attributes.values().removeIf(AttributeMetadata::isIgnored);
    }

    /**
     * Is this function a getter?
     * @param exec The function
     * @return The predicted attribute name if this is a getter, otherwise null
     */
    private String isGetter(ExecutableElement exec) {
        var name = exec.getSimpleName().toString();
        if (name.startsWith("get") && name.length() >= 4 && Character.isUpperCase(name.charAt(3))) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("is") && name.length() >= 3 && Character.isUpperCase(name.charAt(2))) {
            // Must return a boolean or Boolean
            var type = exec.getReturnType();
            if (type.getKind() == TypeKind.BOOLEAN || typeTools.equal(type, typeTools.booleanMirror)) {
                return Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }
        }
        return null;
    }

    /**
     * Is this function a setter?
     * @param exec The method
     * @return The predicted attribute name if this is a setter, otherwise null
     */
    private String isSetter(ExecutableElement exec) {
        var name = exec.getSimpleName().toString();
        if (name.startsWith("set") && name.length() >= 4 && Character.isUpperCase(name.charAt(3))) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else {
            return null;
        }
    }

    /**
     * Build a class that is a dynamo table
     * @return The JavaFile that will write the class
     * @throws CtException On any error generating the table class
     */
    public JavaFile buildTableClass() throws CtException {
        if (partitionKeyAttribute == null) {
            throw new CtException("Tables must have a getter or member variable with @DynamoPartitionKey annotation");
        }
        var tableType = typeTools.types.getDeclaredType(
            typeTools.elements.getTypeElement(DynamoTable.class.getCanonicalName()),
            typeTools.types.getDeclaredType(itemType), attributes.get(partitionKeyAttribute).boxedReturnType,
            sortKeyAttribute == null ? typeTools.voidMirror : attributes.get(sortKeyAttribute).boxedReturnType);
        var classBuilder = TypeSpec.classBuilder(itemType.getSimpleName() + "DynamoTable")
                               .addModifiers(Modifier.PUBLIC)
                               .superclass(ParameterizedTypeName.get(tableType));
        classBuilder.addMethod(buildTableConstructor(true, true))
            .addMethod(buildTableConstructor(true, false))
            .addMethod(buildTableConstructor(false, true))
            .addMethod(buildGetKeyMethod("getPartitionValue", partitionKeyAttribute, true))
            .addMethod(buildGetKeyMethod("getSortValue", sortKeyAttribute, true))
            .addMethod(buildGetKeyMethod("getPartitionValue", partitionKeyAttribute, false))
            .addMethod(buildGetKeyMethod("getSortValue", sortKeyAttribute, false))
            .addMethod(buildKeyToAttributeValueMethod("partitionValueToAttributeValue", partitionKeyAttribute))
            .addMethod(buildKeyToAttributeValueMethod("sortValueToAttributeValue", sortKeyAttribute))
            .addMethod(buildEncoderMethod(false))
            .addMethod(buildDecoderMethod(false))
            .addMethod(buildGetExclusiveStartKeyMethod(partitionKeyAttribute, sortKeyAttribute))
            .addMethod(buildDecodeExclusiveStartMethod(partitionKeyAttribute, sortKeyAttribute))
            .addMethod(buildGetIndexMethod());

        var qualifiedName = itemType.getQualifiedName().toString();
        var packageSplit = qualifiedName.lastIndexOf('.');
        var packageName = (packageSplit > 0 ? qualifiedName.substring(0, packageSplit) : "");
        // Add our indexes
        for (var indexName : indexes.keySet()) {
            var metadata = indexes.get(indexName);
            TypeName name = ClassName.get(packageName, itemType.getSimpleName() + "DynamoTable",
                indexNameToClassName(indexName));

            // Create the class that we return. We can't return the actual class of the index, that is a private inner
            // class, so we have to instead return the parameterized DynamoIndex class that the real index class extends.
            var indexType = typeTools.types.getDeclaredType(
                (TypeElement)typeTools.types.asElement(typeTools.indexMirror),    // DynamoIndex<
                typeTools.types.getDeclaredType(itemType),                        //     ItemType,
                attributes.get(metadata.getPartitonAttribute()).boxedReturnType,  //     PartitionType,
                attributes.get(metadata.getSortAttribute()).boxedReturnType);     //     SortType>

            classBuilder.addType(buildIndexInnerClass(indexName, indexType));
            classBuilder.addMethod(MethodSpec.methodBuilder("get" + indexNameToClassName(indexName))
                                       .addModifiers(Modifier.PUBLIC)
                                       .returns(ParameterizedTypeName.get(indexType))
                                       .addStatement("return new $T(getClient(), getAsyncClient(), getTableName())", name)
                                       .build());
        }

        // Add the member variables for the codecs we need. This must be done after all methods are built, because building
        // methods may find more codecs we need.
        for (var codecEntry : codecClassToCodecVar.entrySet()) {
            var field = FieldSpec.builder(codecEntry.getKey(), codecEntry.getValue(),
                Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                            .initializer(CodeBlock.builder().add("new $T()", codecEntry.getKey()).build());
            classBuilder.addField(field.build());
        }

        return JavaFile.builder(packageName, classBuilder.build()).build();
    }

    /**
     * Build an inner class that extends {@link DynamoIndex}
     * @param indexName The name of the index
     * @param indexType The type that the index extends
     * @return The TypeSpec that will write the inner class
     * @throws CtException On any error generating the index class
     */
    private TypeSpec buildIndexInnerClass(String indexName, DeclaredType indexType) throws CtException {
        var metadata = indexes.get(indexName);
        if (metadata.getPartitonAttribute() == null) {
            throw new CtException("Index " + indexName + " has no partition key", metadata.getDeclaringElement());
        }
        if (metadata.getSortAttribute() == null) {
            throw new CtException("Index " + indexName + " has no sort key", metadata.getDeclaringElement());
        }
        var constructor = MethodSpec.constructorBuilder()
                              .addParameter(DynamoDbClient.class, "client")
                              .addParameter(DynamoDbAsyncClient.class, "asyncClient")
                              .addParameter(String.class, "tableName")
                              .addStatement("super(client, asyncClient, tableName, $S, $S, $S)",
                                  indexName, metadata.getPartitonAttribute(), metadata.getSortAttribute())
                              .build();
        var classBuilder = TypeSpec.classBuilder(indexNameToClassName(indexName))
                               .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                               .superclass(ParameterizedTypeName.get(indexType))
                               .addMethod(constructor)
                               .addMethod(buildKeyToAttributeValueMethod("partitionValueToAttributeValue", metadata.getPartitonAttribute()))
                               .addMethod(buildKeyToAttributeValueMethod("sortValueToAttributeValue", metadata.getSortAttribute()))
                               .addMethod(buildGetKeyMethod("getPartitionValue", metadata.getPartitonAttribute(), true))
                               .addMethod(buildGetKeyMethod("getSortValue", metadata.getSortAttribute(), true))
                               .addMethod(buildEncoderMethod(false))
                               .addMethod(buildDecoderMethod(false))
                               .addMethod(buildGetExclusiveStartKeyMethod(metadata.getPartitonAttribute(), metadata.getSortAttribute()))
                               .addMethod(buildDecodeExclusiveStartMethod(metadata.getPartitonAttribute(), metadata.getSortAttribute()));
        return classBuilder.build();
    }

    /**
     * Create a file that implements a codec for the annotated type
     * @return The JavaFile that can write the codec
     * @throws CtException If there is an error processing the class
     */
    public JavaFile buildCodecClass() throws CtException {
        var codecType = typeTools.types.getDeclaredType(typeTools.elements.getTypeElement(DynamoCodec.class.getCanonicalName()),
            typeTools.types.getDeclaredType(itemType));
        var classBuilder = TypeSpec.classBuilder(itemType.getSimpleName() + "DynamoCodec")
                               .addModifiers(Modifier.PUBLIC)
                               .superclass(ParameterizedTypeName.get(codecType));
        for (var codecEntry: codecClassToCodecVar.entrySet()) {
            var field = FieldSpec.builder(codecEntry.getKey(), codecEntry.getValue(),
                Modifier.PRIVATE, Modifier.FINAL)
                            .initializer(CodeBlock.builder().add("new $T()", codecEntry.getKey()).build());
            classBuilder.addField(field.build());
        }
        classBuilder.addMethod(buildEncoderMethod(true))
            .addMethod(buildDecoderMethod(true));
        var qualifiedName = itemType.getQualifiedName().toString();
        var packageSplit = qualifiedName.lastIndexOf('.');
        return JavaFile.builder(packageSplit > 0 ? qualifiedName.substring(0, packageSplit) : "", classBuilder.build()).build();
    }

    /**
     * Does this element have a ctDynamo annotation?
     * @param element The element to inspect
     * @return true if element has a ctDynamo annotation
     */
    private boolean hasCtDynamoAnnotation(Element element) {
        return element.getAnnotation(DynamoAttribute.class) != null
                   || element.getAnnotation(DynamoPartitionKey.class) != null
                   || element.getAnnotation(DynamoSortKey.class) != null
                   || element.getAnnotation(DynamoSecondaryPartitionKey.class) != null
                   || element.getAnnotation(DynamoSecondarySortKey.class) != null
                   || element.getAnnotation(DynamoIgnore.class) != null;
    }

    /**
     * Process an attribute. It could be a field (which will always have a ctDynamo annotation) or a getter (which might
     * have a ctDynamo annotation)
     * @param declaringElement The element we are processing
     * @param getterTail The name of the getter of this attribute with "get" or "is" stripped out, then downcased
     * @param attributeType The return type of the getter
     * @throws CtException On any error processing the attribute
     */
    private void processAttribute(Element declaringElement, String getterTail, TypeMirror attributeType) throws CtException {
        if (getterTailToAttribute.containsKey(getterTail)) {
            throw new CtException("Multiple annotations found on getters and/or fields named " + getterTail, declaringElement);
        }
        TypeMirror codecType = null;
        var annotationFound = false;
        var attributeAnnotation = declaringElement.getAnnotation(DynamoAttribute.class);
        var nameFromAnnotation = "";
        if (attributeAnnotation != null) {
            annotationFound = true;
            nameFromAnnotation = attributeAnnotation.value();
            codecType = typeTools.getAnnonationClassValue(attributeAnnotation::codec);
        }
        var partitionKeyAnnotation = declaringElement.getAnnotation(DynamoPartitionKey.class);
        if (partitionKeyAnnotation != null) {
            if (annotationFound) {
                throw new CtException("At most one of " + DynamoPartitionKey.class.getSimpleName()
                                             + ", " + DynamoSortKey.class.getSimpleName()
                                             + ", or " + DynamoAttribute.class.getSimpleName()
                                             + " may be provided for each method", declaringElement);
            }
            annotationFound = true;
            if (partitionKeyAttribute != null) {
                throw new CtException("Cannot have multiple partition keys", declaringElement);
            }
            nameFromAnnotation = partitionKeyAnnotation.value();
            partitionKeyAttribute = nameFromAnnotation.isEmpty() ? getterTail : nameFromAnnotation;
            codecType = typeTools.getAnnonationClassValue(partitionKeyAnnotation::codec);
        }
        var sortKeyAnnotation = declaringElement.getAnnotation(DynamoSortKey.class);
        if (sortKeyAnnotation != null) {
            if (annotationFound) {
                throw new CtException("At most one of " + DynamoPartitionKey.class.getSimpleName()
                                             + ", " + DynamoSortKey.class.getSimpleName()
                                             + ", or " + DynamoAttribute.class.getSimpleName()
                                             + " may be provided for each method", declaringElement);
            }
            annotationFound = true;
            if (sortKeyAttribute != null) {
                throw new CtException("Cannot have multiple sort keys", declaringElement);
            }
            nameFromAnnotation = sortKeyAnnotation.value();
            sortKeyAttribute = (nameFromAnnotation.isEmpty() ? getterTail : nameFromAnnotation);
            codecType = typeTools.getAnnonationClassValue(sortKeyAnnotation::codec);
        }
        var attributeName = nameFromAnnotation.isEmpty() ? getterTail : nameFromAnnotation;
        var secondaryPartitionKeyAnnotation = declaringElement.getAnnotation(DynamoSecondaryPartitionKey.class);
        if (secondaryPartitionKeyAnnotation != null) {
            annotationFound = true;
            for (var indexName : secondaryPartitionKeyAnnotation.value()) {
                indexes.computeIfAbsent(indexName, index -> new IndexMetadata()).setPartitonAttribute(attributeName, declaringElement);
            }
        }
        var secondarySortKeyAnnotation = declaringElement.getAnnotation(DynamoSecondarySortKey.class);
        if (secondarySortKeyAnnotation != null) {
            annotationFound = true;
            for (var indexName: secondarySortKeyAnnotation.value()) {
                indexes.computeIfAbsent(indexName, index -> new IndexMetadata()).setSortAttribute(attributeName, declaringElement);
            }
        }
        AttributeMetadata attributeMetadata;
        if (declaringElement.getAnnotation(DynamoIgnore.class) == null) {
            var codecName = (codecType == null || typeTools.equal(typeTools.defaultCodecMirror, codecType) ? null : TypeName.get(codecType));
            if (codecName == null) {
                codecName = findCodecClass(attributeType);
            } else {
                addCodec(codecName);
            }
            attributeMetadata = new AttributeMetadata(attributeName, attributeType, typeTools.box(attributeType), codecName, declaringElement);
        } else {
            if (annotationFound) {
                throw new CtException(DynamoIgnore.class.getSimpleName() + " is incompatible with other dynamo annotations", declaringElement);
            }
            attributeMetadata = new AttributeMetadata(declaringElement);
        }
        if (attributes.put(attributeName, attributeMetadata) != null) {
            // Oops, already had a hard metadata. The annotations must all be on the same element.
            throw new CtException("Two elements of attribute " + attributeName
                                      + " include dynamo annotations; all annotations must be on the same elements", declaringElement);
        }
        getterTailToAttribute.put(getterTail, attributeMetadata);
    }

    /**
     * Build a constructor for a DynamoTable class
     * @param withSyncClient true if we should have a sync client parameter
     * @param withAsyncClient true if we should have an async client parameter
     * @return The constructor
     */
    private MethodSpec buildTableConstructor(boolean withSyncClient, boolean withAsyncClient) {
        var builder = MethodSpec.constructorBuilder()
                          .addModifiers(Modifier.PUBLIC);
        if (withSyncClient) {
            builder.addParameter(DynamoDbClient.class, "client");
        }
        if (withAsyncClient) {
            builder.addParameter(DynamoDbAsyncClient.class, "asyncClient");
        }
        builder.addParameter(String.class, "tableName");
        if (withSyncClient && withAsyncClient) {
            builder.addStatement("super(client, asyncClient, tableName, $S, $S)", partitionKeyAttribute, sortKeyAttribute);
        } else if (withSyncClient) {
            builder.addStatement("super(client, null, tableName, $S, $S)", partitionKeyAttribute, sortKeyAttribute);
        } else if (withAsyncClient) {
            builder.addStatement("super(null, asyncClient, tableName, $S, $S)", partitionKeyAttribute, sortKeyAttribute);
        } else {
            throw new IllegalArgumentException("withSyncClient and withAsyncClient are both null, one must be provided");
        }
        return builder.build();
    }

    /**
     * Build a "getXxxKey" method
     * @param getKeyName The name of the method we will build
     * @param attributeName The attribute name of this key
     * @param fromItem true if we are getting the key from a full item, false if we have the attribute value and need
     *                 to call its codec or wrap it in an AttributeValue
     * @return The method spec
     * @throws CtException On any error building the method
     */
    private MethodSpec buildGetKeyMethod(String getKeyName, String attributeName, boolean fromItem) throws CtException {
        var methodBuilder = MethodSpec.methodBuilder(getKeyName)
                                .addAnnotation(Override.class)
                                .addModifiers(fromItem ? Modifier.PUBLIC : Modifier.PROTECTED, Modifier.FINAL);
        if (fromItem) {
            methodBuilder.addParameter(TypeName.get(typeTools.types.getDeclaredType(itemType)), "value");
        } else {
            methodBuilder.addParameter(AttributeValue.class, "value");
        }
        if (attributeName == null) {
            // A nonexistant sort key. Return a Void that is null.
            methodBuilder.returns(Void.class)
                .addStatement("return null");
        } else {
            var parameterMetadata = attributes.get(attributeName);
            methodBuilder.returns(TypeName.get(parameterMetadata.boxedReturnType));
            if (fromItem) {
                if (parameterMetadata.returnType.getKind().isPrimitive()) {
                    // Cannot be null
                    methodBuilder.addStatement("return value." + parameterMetadata.getGetterName() + "()");
                } else {
                    // Check for null
                    methodBuilder.addStatement("$T key = value." + parameterMetadata.getGetterName() + "()", parameterMetadata.returnType)
                        .beginControlFlow("if (key == null)")
                        .addStatement("throw new $T($S)", NullPointerException.class,
                            "Null "
                                + (attributeName.equals(partitionKeyAttribute) ? "partition" : "sort")
                                + " key attribute \"" + attributeName + "\"")
                        .endControlFlow()
                        .addStatement("return key");
                }
            } else {
                methodBuilder.beginControlFlow("if ((value == null) || (value.nul() == $T.TRUE))", Boolean.class)
                    .addStatement("return null")
                    .nextControlFlow("else");
                var formatParams = new HashMap<String, Object>();
                methodBuilder.addStatement(
                    CodeBlock.builder().addNamed(
                        "return " + buildAttributeDecodeExpression("value", parameterMetadata.codecClass, parameterMetadata.returnType, formatParams), formatParams)
                        .build());
                methodBuilder.endControlFlow();
            }
        }
        return methodBuilder.build();
    }

    /**
     * Build a method that converts a key value to an AttributeValue
     * @param methodName The name of the method we build
     * @param attribute The attribute to build for
     * @return A method that converts the key value to an attribute value
     * @throws CtException On any error building this method
     */
    private MethodSpec buildKeyToAttributeValueMethod(String methodName, String attribute) throws CtException {
        var metadata = (attribute == null ? null : attributes.get(attribute));
        var methodBuilder = MethodSpec.methodBuilder(methodName)
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
                                .returns(AttributeValue.class);
        if (metadata == null) {
            methodBuilder.addParameter(Void.class, "value");
            methodBuilder.addStatement("throw new $T($S)", UnsupportedOperationException.class,
                "This table has no sort key");
        } else {
            methodBuilder.addParameter(TypeName.get(metadata.boxedReturnType), "value");
            var formatParams = new HashMap<String, Object>();
            methodBuilder.addNamedCode("return " + buildAttributeEncodeExpression(attribute, "value", formatParams) + ";\n", formatParams);
        }
        return methodBuilder.build();
    }

    /**
     * Build a method to encode the DynamoItem-annotated class
     * @param toAttributeValue true to encode into a single attribute value, false to encode to a Map&lt;String, AttributeValue>
     * @return The method that does the encoding
     * @throws CtException On any error building the method
     */
    private MethodSpec buildEncoderMethod(boolean toAttributeValue) throws CtException {
        var builder = MethodSpec.methodBuilder("encode")
                          .addAnnotation(Override.class)
                          .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                          .addParameter(TypeName.get(typeTools.types.getDeclaredType(itemType)), "value")
                          .returns(toAttributeValue ? TypeName.get(AttributeValue.class) : TypeName.get(typeTools.dynamoMapMirror))
                          .addStatement("$T map = new $T($L)", ATTRIBUTE_VALUE_HASH_MAP, ATTRIBUTE_VALUE_HASH_MAP, (attributes.size() * 4 + 2) / 3);
        var formatParams = new HashMap<String, Object>();
        for (var entry : attributes.entrySet()) {
            var attributeName = entry.getKey();
            var kind = entry.getValue().returnType.getKind();
            formatParams.clear();
            var attrNameParam = getUniqueId("s");
            formatParams.put(attrNameParam, attributeName);
            if (kind.isPrimitive()) {
                builder.addNamedCode("map.put($" + attrNameParam + ":S, " + buildAttributeEncodeExpression(attributeName, null, formatParams) + ");\n", formatParams);
            } else {
                // Non-primitives. May be null
                var varName = upcaseFirst("v", attributeName);  // Prepend a "v" and upcase to make sure it is not a reserved word, or the name "map" or "value"
                builder.addStatement("$T " + varName + " = value." + entry.getValue().getGetterName() + "()", TypeName.get(entry.getValue().returnType));
                if (attributeName.equals(partitionKeyAttribute) || attributeName.equals(sortKeyAttribute)) {
                    builder.beginControlFlow("if (" + varName + " == null)")
                        .addStatement("throw new $T($S)", NullPointerException.class,
                            "Null primary "
                                + (attributeName.equals(partitionKeyAttribute) ? "partition" : "sort")
                                + " key attribute \"" + attributeName + "\"")
                        .endControlFlow()
                        .addNamedCode("map.put($" + attrNameParam + ":S, " + buildAttributeEncodeExpression(attributeName, varName, formatParams) + ");\n", formatParams);
                } else if (ignoreNulls) {
                    builder.beginControlFlow("if (" + varName + " != null)")
                        .addNamedCode("map.put($" + attrNameParam + ":S, " + buildAttributeEncodeExpression(attributeName, varName, formatParams) + ");\n", formatParams)
                        .endControlFlow();
                } else {
                    var codecClassParam = getUniqueId("t");
                    formatParams.put(codecClassParam, DynamoCodec.class);
                    builder.addNamedCode("map.put($" + attrNameParam + ":S, " + varName + " == null ? $" + codecClassParam + ":T.NULL_ATTRIBUTE_VALUE : "
                                             + buildAttributeEncodeExpression(attributeName, varName, formatParams) + ");\n", formatParams);
                }
            }
        }
        if (toAttributeValue) {
            builder.addStatement("return $T.builder().m(map).build()", AttributeValue.class);
        } else {
            builder.addStatement("return map");
        }
        return builder.build();
    }

    /**
     * Build a decoder method
     * @param fromAttributeValue true if our input in an AttributeValue, false if it is a Map&lt;String, AttributeValue>
     * @return A method that decodes the annotated class
     * @throws CtException On error building the decoder
     */
    private MethodSpec buildDecoderMethod(boolean fromAttributeValue) throws CtException {
        var entryTypeName = TypeName.get(typeTools.types.getDeclaredType(itemType));
        var builder = MethodSpec.methodBuilder("decode")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(entryTypeName)
            .addStatement("$T result = new $T()", entryTypeName, entryTypeName);
        if (fromAttributeValue) {
            builder.addParameter(TypeName.get(AttributeValue.class), "value")
                .addStatement("$T map = value.m()", typeTools.dynamoMapMirror);
        } else {
            builder.addParameter(TypeName.get(typeTools.dynamoMapMirror), "map");
        }
        builder.addStatement("$T attribute", AttributeValue.class);
        var formatParams = new HashMap<String, Object>();
        for (var entry: attributes.entrySet()) {
            formatParams.clear();
            if (entry.getKey().equals(partitionKeyAttribute) || entry.getKey().equals(sortKeyAttribute)) {
                // Cannot be null. Just call the setter.
                formatParams.put("a", entry.getKey());
                builder.addNamedCode("result." + entry.getValue().getSetterName() + "("
                                         + buildAttributeDecodeExpression("map.get($a:S)", entry.getValue().codecClass, entry.getValue().returnType, formatParams)
                                         + ");\n", formatParams);
            } else {
                builder.addStatement("attribute = map.get($S)", entry.getKey());
                var expression = buildAttributeDecodeExpression("attribute", entry.getValue().codecClass, entry.getValue().returnType, formatParams);
                if (ignoreNulls || entry.getValue().returnType.getKind().isPrimitive()) {
                    // With ignore nulls or a primitive type, we ignore null attributes.
                    // Primitive types perhaps should throw exceptions when they see an explicit null value, but that is
                    // a dangerous game to play.
                    builder.beginControlFlow("if (attribute != null && attribute.nul() != $T.TRUE)", Boolean.class)
                        .addNamedCode("result." + entry.getValue().getSetterName() + "("
                                          + expression
                                          + ");\n", formatParams)
                        .endControlFlow();
                } else {
                    // If we have a nullable field and we don't ignore nulls, then we explicitly set the value to null.
                    // This will be unnecessary in most cases, but if the class has a nullable field with a non-null value
                    // then it will be needed.
                    formatParams.put("b", Boolean.class);
                    builder.addNamedCode("result." + entry.getValue().getSetterName() + "(attribute == null || attribute.nul() == $b:T.TRUE ? null : "
                                             + expression + ");\n", formatParams);
                }
            }
        }
        return builder.addStatement("return result").build();
    }

    /**
     * Build the "getIndex" method. It can have three forms, depending on how many indexes there are.
     * @return The MethodSpec for getIndex()
     */
    private MethodSpec buildGetIndexMethod() {
        var partitionT = TypeVariableName.get("IndexPartitionT");
        var sortT = TypeVariableName.get("IndexSortT");
        var returnT = ParameterizedTypeName.get(ClassName.get(DynamoIndex.class), TypeName.get(itemType.asType()), partitionT, sortT);
        var builder = MethodSpec.methodBuilder("getIndex")
                          .addModifiers(Modifier.PUBLIC)
                          .addAnnotation(Override.class)
                          .addTypeVariable(partitionT)
                          .addTypeVariable(sortT)
                          .returns(returnT)
                          .addParameter(String.class, "name")
                          .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), partitionT), "partitionClass")
                          .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), sortT), "sortClass");
        switch (indexes.size()) {
            case 0:
                addGetIndexNoIndexesCase(builder);
                break;
            case 1:
                addGetIndexOneIndexCase(builder, returnT);
                break;
            default:
                addGetIndexMultipleIndexesCase(builder, returnT);
                break;
        }
        return builder.build();
    }

    /**
     * Add the body for getIndex() in the case where the table has no indexes
     * @param builder The MethodSpec builder
     */
    private void addGetIndexNoIndexesCase(MethodSpec.Builder builder) {
        builder.addStatement("throw new $T($S)", UnsupportedOperationException.class,
            "Class " + itemType.getSimpleName() + " has no secondary indexes. Maybe you are missing @"
                + DynamoSecondaryPartitionKey.class.getSimpleName() + " annotations").build();
    }

    /**
     * Add the body for getIndex() in the case where the table has one index
     * @param builder The MethodSpec builder
     * @param returnT The return type of getIndex()
     */
    private void addGetIndexOneIndexCase(MethodSpec.Builder builder, ParameterizedTypeName returnT) {
        var entry = indexes.entrySet().iterator().next();
        var partitionType = attributes.get(entry.getValue().getPartitonAttribute()).boxedReturnType;
        var sortType = attributes.get(entry.getValue().getSortAttribute()).boxedReturnType;
        builder.beginControlFlow("if (name.equals($S))", entry.getKey())
            .beginControlFlow("if (((partitionClass == null) || (partitionClass == $T.class))"
                                  + " && ((sortClass == null) || (sortClass == $T.class)))", partitionType, sortType)
            .addStatement(upcaseFirst("return ($T)get", entry.getKey()) + "Index()", returnT)
            .nextControlFlow("else")
            .addStatement("throw new $T($S + partitionClass.getSimpleName() + $S + sortClass.getSimpleName())",
                IllegalArgumentException.class, "Incorrect key types for index " + entry.getKey() + ", expected: "
                                                    + partitionType.asElement().getSimpleName()
                                                    + " and "
                                                    + sortType.asElement().getSimpleName()
                                                    + ", got: ",
                " and ")
            .endControlFlow()
            .nextControlFlow("else")
            .addStatement("throw new $T($S + name)", IllegalArgumentException.class, "Unknown index: ")
            .endControlFlow()
            .build();
    }

    /**
     * Add the body for getIndex() in the case where there is more than one index in the table
     * @param builder The MethodSpec builder
     * @param returnT The return type for getIndex()
     */
    private void addGetIndexMultipleIndexesCase(MethodSpec.Builder builder, ParameterizedTypeName returnT) {
        builder.addStatement("$T expectedPartitionClass", ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
            .addStatement("$T expectedSortClass", ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
            .addStatement("$T index", ParameterizedTypeName.get(ClassName.get(DynamoIndex.class),
                TypeName.get(itemType.asType()), WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class)))
            .beginControlFlow("switch(name)");
        for (var indexName : indexes.keySet()) {
            var metadata = indexes.get(indexName);
            builder.addCode("case $S:\n", indexName)
                .addStatement("expectedPartitionClass = $T.class", attributes.get(metadata.getPartitonAttribute()).boxedReturnType)
                .addStatement("expectedSortClass = $T.class", attributes.get(metadata.getSortAttribute()).boxedReturnType)
                .addStatement(upcaseFirst("index = get", indexName) + "Index()")
                .addStatement("break");
        }
        builder.addCode("default:\n")
            .addStatement("throw new $T($S + name)", IllegalArgumentException.class,
                "Unknown index name: ");
        builder.endControlFlow();
        builder.beginControlFlow("if (((partitionClass != null) && (partitionClass != expectedPartitionClass))"
                                     + " || ((sortClass != null) && (sortClass != expectedSortClass)))")
            .addStatement("throw new $T($S + name + $S + expectedPartitionClass.getSimpleName() + $S + expectedSortClass.getSimpleName() + $S + partitionClass.getSimpleName() + $S + sortClass.getSimpleName())",
                IllegalArgumentException.class, "Incorrect key types for index ", ", expected: ", " and ", ", got: ", " and ")
            .endControlFlow();
        builder.addStatement("return ($T)index", returnT);
    }

    /**
     * Build the getExclusiveStart() method
     * @param indexPartitionKeyAttribute The attribute name for the partition key
     * @param indexSortKeyAttribute The attribute name for the sort key, or null if there is none
     * @return The MethodSpec for getExclusiveStart
     */
    private MethodSpec buildGetExclusiveStartKeyMethod(String indexPartitionKeyAttribute, String indexSortKeyAttribute) {
        var builder = MethodSpec.methodBuilder("getExclusiveStartKey")
                          .addModifiers(Modifier.PROTECTED)
                          .addAnnotation(Override.class)
                          .returns(String.class)
                          .addParameter(ParameterizedTypeName.get(Map.class, String.class, AttributeValue.class), "item")
                          .addStatement("$T builder = new $T()", StringBuilder.class, StringBuilder.class);
        // Add our partition key to the start key
        builder.addStatement(writeGetExclusiveStartStatement(indexPartitionKeyAttribute));

        // If we have a sort key, add it to the start key
        if (indexSortKeyAttribute != null) {
            builder.addStatement("builder.append(',')")
                .addStatement(writeGetExclusiveStartStatement(indexSortKeyAttribute));
        }

        // If we are an index, and our table's partition key that isn't our partition or sort keys, then add it
        if (!partitionKeyAttribute.equals(indexPartitionKeyAttribute) && !partitionKeyAttribute.equals(indexSortKeyAttribute)) {
            builder.addStatement("builder.append(',')")
                .addStatement(writeGetExclusiveStartStatement(partitionKeyAttribute));
        }

        // If we are an index, and our table has a sort key that isn't our partition or sort keys, then add it
        if ((sortKeyAttribute != null) && !sortKeyAttribute.equals(indexPartitionKeyAttribute) && !sortKeyAttribute.equals(indexSortKeyAttribute)) {
            builder.addStatement("builder.append(',')")
                .addStatement(writeGetExclusiveStartStatement(sortKeyAttribute));
        }
        return builder.addStatement("return builder.toString()").build();
    }

    /**
     * Add a statement writing one value for the getExclusiveStart() method
     * @param attributeName The name of the attribute
     * @return The code block that adds the attribute to the exclusive start key
     */
    private CodeBlock writeGetExclusiveStartStatement(String attributeName) {
        return CodeBlock.builder()
                   .add("appendExclusiveStartValue(builder, item.get($S)."
                            + (typeTools.isNumber(attributes.get(attributeName).returnType)
                               ? "n())"
                               : "s())"),
                       attributeName).build();
    }

    /**
     * Build the method that decodes an exclusive start key
     * @param indexPartitionKeyAttribute Our partition key attribute name
     * @param indexSortKeyAttribute Our sort key attribute name
     * @return A MethodSpec for decodeExclusiveStart()
     */
    private MethodSpec buildDecodeExclusiveStartMethod(String indexPartitionKeyAttribute, String indexSortKeyAttribute) {
        var avMap = ParameterizedTypeName.get(Map.class, String.class, AttributeValue.class);
        var builder = MethodSpec.methodBuilder("decodeExclusiveStart")
                          .addModifiers(Modifier.PROTECTED)
                          .addAnnotation(Override.class)
                          .returns(avMap)
                          .addParameter(String.class, "exclusiveStart");
        var numEntries = 1;
        var values = new HashMap<String, Object>();
        values.put("av", AttributeValue.class);
        values.put("m", Map.class);
        var template = new StringBuilder("return $m:T.of(");
        writeDecodeExclusiveStartStatement(template, values, indexPartitionKeyAttribute, 0);
        if (indexSortKeyAttribute != null) {
            writeDecodeExclusiveStartStatement(template, values, indexSortKeyAttribute, numEntries++);
        }
        if (!partitionKeyAttribute.equals(indexPartitionKeyAttribute) && !partitionKeyAttribute.equals(indexSortKeyAttribute)) {
            writeDecodeExclusiveStartStatement(template, values, partitionKeyAttribute, numEntries++);
        }
        if ((sortKeyAttribute != null) && !sortKeyAttribute.equals(indexPartitionKeyAttribute) && !sortKeyAttribute.equals(indexSortKeyAttribute)) {
            writeDecodeExclusiveStartStatement(template, values, sortKeyAttribute, numEntries++);
        }
        template.append(")");
        return builder
                   .addStatement("$T[] values = new $T[" + numEntries + "]", String.class, String.class)
                   .addStatement("splitExclusiveStartValues(values, exclusiveStart)")
                   .addStatement(CodeBlock.builder().addNamed(template.toString(), values).build()).build();
    }

    /**
     * Write a statement to store one attribute from an exclusive start key into an attribute value map
     * @param template The StringBuilder we should write our statement to
     * @param values The values that will be used to complete the template
     * @param attributeName The name of the attribute we are storing
     * @param index Which element of our array of strings holds our value
     */
    private void writeDecodeExclusiveStartStatement(StringBuilder template, Map<String, Object> values, String attributeName, int index) {
        if (index > 0) {
            template.append(", ");
        }
        template.append("$a")
            .append(index)
            .append(":S, $av:T.builder().")
            .append(typeTools.isNumber(attributes.get(attributeName).returnType)
                               ? 'n'
                               : 's')
            .append("(values[")
            .append(index)
            .append("]).build()");
        values.put("a" + index, attributeName);
    }

    /**
     * Turn an index name into the name of a class. Replace illegal java class name characters with underscores, upcase
     * the first character, and append the word "Index" at the end.
     * @param indexName The name of the index
     * @return An inner class name.
     */
    private String indexNameToClassName(String indexName) {
        if (Character.isDigit(indexName.charAt(0))) {
            indexName = "n" + indexName;  // Prepend a "n" so we don't start with a digit.
        }
        return upcaseFirst("", indexName.replace('.', '_').replace('-', '_')) + "Index";
    }

    /**
     * Upcase the first letter of a string then append it to a prefix
     * @param prefix The prefix for the string
     * @param value The non-upcased string
     * @return The result of appending the two strings, upcasing the first letter of the second one
     */
    private String upcaseFirst(String prefix, String value) {
        return prefix + Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Build an expression that encodes an attribute
     * @param attributeName The name of the attribute
     * @param valueVar The variable that holds the attribute value
     * @param formatData Data that will be substituted into the template we return
     * @return The attribute encoding expression
     * @throws CtException If there is an error building the expression
     */
    private String buildAttributeEncodeExpression(String attributeName, String valueVar, Map<String, Object> formatData) throws CtException {
        var metadata = Objects.requireNonNull(attributes.get(Objects.requireNonNull(attributeName, "Null attribute name")), "No metadata for " + attributeName);
        if (valueVar == null) {
            valueVar = "value." + metadata.getGetterName() + "()";
        }
        return buildAttributeEncodeExpression(valueVar, metadata.codecClass, metadata.returnType, formatData, metadata.element);
    }

    /**
     * Build an expression that encodes an attribute
     * @param valueVar The variable that holds the attribute value
     * @param codecClass The class to use to encode/decode the value
     * @param returnType The type of the value
     * @param formatData Data that will be substituted into the template we return
     * @param element The element to identify in errors
     * @return The attribute encoding expression
     * @throws CtException If there is an error building the expression
     */
    private String buildAttributeEncodeExpression(String valueVar, TypeName codecClass, TypeMirror returnType, Map<String, Object> formatData,
                                                  Element element) throws CtException {

        if (codecClass == null) {
            var avId = getUniqueId("t");
            var typeId = getUniqueId("t");
            formatData.put(avId, AttributeValue.class);
            switch (returnType.getKind()) {
                case INT:
                    formatData.put(typeId, Integer.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case LONG:
                    formatData.put(typeId, Long.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case BYTE:
                    formatData.put(typeId, Byte.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case FLOAT:
                    formatData.put(typeId, Float.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case DOUBLE:
                    formatData.put(typeId, Double.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case SHORT:
                    formatData.put(typeId, Short.class);
                    return "$" + avId + ":T.builder().n($" + typeId + ":T.toString(" + valueVar + ")).build()";
                case BOOLEAN:
                    return "$" + avId + ":T.builder().bool(" + valueVar + ").build()";
                case DECLARED:
                    break;
                default:
                    throw new CtException("Unknown typeKind " + returnType.getKind());
            }
            if (typeTools.equal(returnType, typeTools.stringMirror)) {
                return "$" + avId + ":T.builder().s(" + valueVar + ").build()";
            } else if (typeTools.types.isSubtype(returnType, typeTools.listMirror) || typeTools.types.isSubtype(returnType, typeTools.setMirror)) {
                var innerType = ((DeclaredType)returnType).getTypeArguments().get(0);
                var tmpVar = getUniqueId("t");
                var collectors = getUniqueId("t");
                formatData.put(collectors, Collectors.class);
                return "$" + avId + ":T.builder().l(" + valueVar + ".stream()"
                           + ".map(" + tmpVar + " -> " + buildAttributeEncodeExpression(tmpVar, null, innerType, formatData, element) + ")"
                           + ".collect($" + collectors + ":T.toList())).build()";
            } else if (typeTools.types.isSubtype(returnType, typeTools.enumMirror)) {
                return "$" + avId + ":T.builder().s(" + valueVar + ".name()).build()";
            } else if (typeTools.equal(returnType, typeTools.booleanMirror)) {
                return "$" + avId + ":T.builder().bool(" + valueVar + ").build()";
            } else if (typeTools.isNumber(returnType)) {
                return "$" + avId + ":T.builder().n(" + valueVar + ".toString()).build()";
            } else {
                // See if we can find a codec for this class. Otherwise we can't encode it.
                codecClass = findCodecClass(returnType);
                if (codecClass == null) {
                    throw new CtException("Don't know how to encode class " + returnType, element);
                } else {
                    return codecClassToCodecVar.get(codecClass) + ".encode(" + valueVar + ")";
                }
            }
        } else {
            // We have a codec for this class. Simply call it.
            return codecClassToCodecVar.get(codecClass) + ".encode(" + valueVar + ")";
        }
    }

    /**
     * Build an expression to decode a given attribute
     * @param valueVar The variable name holding the AttributeValue
     * @param codecClass The class of codec to use, or null to not use a codec
     * @param returnType The data type to return
     * @param formatData Values that will be plugged into the string returned
     * @return An expression to decode the given attribute
     * @throws CtException If there is an error building the expression
     */
    private String buildAttributeDecodeExpression(String valueVar, TypeName codecClass, TypeMirror returnType, Map<String, Object> formatData)
        throws CtException {
        if (codecClass == null) {
            var typeId = getUniqueId("t");
            switch (returnType.getKind()) {
                case INT:
                    formatData.put(typeId, Integer.class);
                    return "$" + typeId + ":T.parseInt(" + valueVar + ".n())";
                case LONG:
                    formatData.put(typeId, Long.class);
                    return "$" + typeId + ":T.parseLong(" + valueVar + ".n())";
                case BYTE:
                    formatData.put(typeId, Byte.class);
                    return "$" + typeId + ":T.parseByte(" + valueVar + ".n())";
                case FLOAT:
                    formatData.put(typeId, Float.class);
                    return "$" + typeId + ":T.parseFloat(" + valueVar + ".n())";
                case DOUBLE:
                    formatData.put(typeId, Double.class);
                    return "$" + typeId + ":T.parseDouble(" + valueVar + ".n())";
                case SHORT:
                    formatData.put(typeId, Short.class);
                    return "$" + typeId + ":T.parseShort(" + valueVar + ".n())";
                case BOOLEAN:
                    return valueVar + ".bool()";
                case DECLARED:
                    break;
                default:
                    throw new CtException("Unknown typeKind " + returnType.getKind());
            }
            if (typeTools.equal(returnType, typeTools.stringMirror)) {
                return valueVar + ".s()";
            } else if (typeTools.types.isSubtype(returnType, typeTools.listMirror) || typeTools.types.isSubtype(returnType, typeTools.setMirror)) {
                var collectorFunc = (typeTools.types.isSubtype(returnType, typeTools.listMirror) ? "toList" : "toSet");
                var innerType = ((DeclaredType)returnType).getTypeArguments().get(0);
                var tmpVar = getUniqueId("t");
                var collectors = getUniqueId("t");
                formatData.put(collectors, Collectors.class);
                return valueVar + ".l().stream()"
                           + ".map(" + tmpVar + " -> " + buildAttributeDecodeExpression(tmpVar, null, innerType, formatData) + ")"
                           + ".collect($" + collectors + ":T." + collectorFunc + "())";
            } else if (typeTools.isNumber(returnType)) {
                formatData.put(typeId, returnType);
                return "$" + typeId + ":T.valueOf(" + valueVar + ".n())";
            } else if (typeTools.equal(returnType, typeTools.booleanMirror)) {
                return valueVar + ".bool()";
            } else if (typeTools.types.isSubtype(returnType, typeTools.enumMirror)) {
                formatData.put(typeId, returnType);
                return "$" + typeId + ":T.valueOf(" + valueVar + ".s())";
            } else {
                // See if we can find a codec for this class. Otherwise we can't decode it.
                codecClass = findCodecClass(returnType);
                if (codecClass == null) {
                    throw new CtException("Don't know how to decode class " + returnType);
                } else {
                    return codecClassToCodecVar.get(codecClass) + ".decode(" + valueVar + ")";
                }
            }
        } else {
            // We have a codec for this class. Simply call it.
            return codecClassToCodecVar.get(codecClass) + ".decode(" + valueVar + ")";
        }
    }

    /**
     * Find the codec class for a given TypeMirror
     * @param baseType The type we need the codec class for
     * @return The codec class if one is declared, otherwise null
     */
    private TypeName findCodecClass(TypeMirror baseType) {
        if (baseType.getKind() == TypeKind.DECLARED) {
            // Check to see if this is based on a class that has a DynamoItem annotation
            var itemAnnotation = ((DeclaredType)baseType).asElement().getAnnotation(DynamoItem.class);
            if (itemAnnotation != null && Arrays.asList(itemAnnotation.value()).contains(DynamoItem.Output.CODEC)) {
                // This gets a little tricky. We can't just create a TypeMirror, because the codec class will be generated by
                // us, so it doesn't exist yet. I think be working with the "stage" system of annotation processing we can
                // delay until it is created, but it's easier to do these steps to create a ClassName object for a nonexistant
                // class.
                var baseClassName = (ClassName)TypeName.get(baseType);
                var codecName = ClassName.get(baseClassName.packageName(), baseClassName.simpleName() + "DynamoCodec");
                addCodec(codecName);
                return codecName;
            }
        }
        return null;
    }

    /**
     * Add a codec to our list of known codecs
     * @param codecName The name of the codec
     */
    private void addCodec(TypeName codecName) {
        codecClassToCodecVar.computeIfAbsent(codecName, klass -> getUniqueId("CODEC_"));
    }

    /**
     * Get a unique ID suitable for a local variable
     * @param prefix A prefix to put in front of the ID
     * @return An ID that is unique within the class
     */
    private String getUniqueId(String prefix) {
        return prefix + ++paramNumber;
    }
}
