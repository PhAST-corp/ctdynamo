package ai.phast.ctdynamo.annotations;

import ai.phast.ctdynamo.DynamoCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This attribute can be applied to a getter or a member variable to indicate the attribute name and/or codec to use
 * to store this in dynamo. If a value is a primary key value, then specify the attribute name and codec there; you
 * may not specify this and primary key information on the same value.
 */
@Target({ElementType.FIELD, ElementType.METHOD}) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoAttribute {

    /**
     * The name of the attribute to be used in Dynamo. Default to a name based on the getter
     * @return The name of the attribute to be used in Dynamo
     */
    String value() default "";

    /**
     * The codec to use to encode/decode this value. For common types (numbers, strings, and enums) this does not need
     * to be specified, there are default codecs built in to ctdynamo for those types
     * @return The codec used to encode/decode this value
     */
    Class<? extends DynamoCodec<?>> codec() default DefaultCodec.class;
}
