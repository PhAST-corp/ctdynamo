package ai.phast.ctdynamo.annotations;

import ai.phast.ctdynamo.DynamoCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This attribute can be applied to a getter or a member variable to indicate that this is the primary partition key of a
 * dynamo table. The attribute name and/or codec to use can also be specified here
 */
@Target({ElementType.FIELD, ElementType.METHOD}) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoPartitionKey {

    /**
     * The attribute name for this value. By default the name is equal to the getter name with "get" stripped out and
     * the first letter converted to lower case
     * @return The attribute name for this value
     */
    String value() default "";

    /**
     * The codec to use to encode/decode this value. For common types (numbers, strings, and enums) this does not need
     * to be specified, there are default codecs built in to ctdynamo for those types
     * @return The codec used to encode/decode this value
     */
    Class<? extends DynamoCodec<?>> codec() default DefaultCodec.class;
}
