package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Class that converts a value to or from a Dynamo AttributeValue object
 * @param <T> The data type that this converts
 */
public abstract class DynamoCodec<T> {

    /**
     * If we don't suppress nulls, then we'll need a lot null attribute values. Since they are all identical and
     * immutable we may as well build one at initialization time and be done.
     */
    public static final AttributeValue NULL_ATTRIBUTE_VALUE = AttributeValue.builder().nul(true).build();

    /**
     * Convert a value to an AttributeValue
     * @param value The value to encode
     * @return The value, represented as an AttributeValue
     */
    public abstract AttributeValue encode(T value);

    /**
     * Convert an AttributeValue to a value
     * @param dynamoValue The AttributeValue to decode
     * @return The value
     */
    public abstract T decode(AttributeValue dynamoValue);
}
