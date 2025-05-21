package ai.phast.ctdynamo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The primary annotation to trigger dynamo processing. This should be put on any item that will be stored in a dynamo
 * table. If it is the top level item, the value should be {@link Output#TABLE}. If this will be stored inside objects
 * that are stored in a dynamo table, then the value should be {@link Output#CODEC}. If the annotated class is used in
 * both ways, then the value should be {{@link Output#TABLE}, {@link Output#CODEC}}.
 */
@Target(ElementType.TYPE) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoItem {

    /** Indicates what kind of dynamo class to write */
    enum Output {
        /** Write a table. This requires the partition/sort keys be specified */
        TABLE,

        /**
         * Write a codec. A codec is for beans that are stored inside another bean in a table, so they have no
         * partition or sort keys
         */
        CODEC
    }

    /**
     * What kind of dynamo class are we producing? By default, a table. You can also produce a codec, or both a table
     * and a codec.
     * @return  A list of the dynamo classes that need to be written.
     */
    Output[] value() default {Output.TABLE};

    /**
     * If true (the default), nulls are not stored in the Dynamo attribute-value map. If false, then dynamo NULL
     * objects are added for null values.
     * @return true if nulls should not be stored in the table
     */
    boolean ignoreNulls() default true;

    /**
     * If true (the default), we first let all other annotation processors run, then process the original class. This is needed
     * if you use a processor like lombok that modifies the original class; java doesn't provide a way to guarantee
     * what order annotation processors run in, so when defer is set, we create a throwaway class that points back
     * to the class you want to process, and we process the original class during the phase of the throwaway.
     * @return true if we should let other annotation processors run before dynamo examines this class
     */
    boolean defer() default true;

    /**
     * If this is set, then it is a list of index key/index name pairs. They key is how the DynamoDB servers name the
     * index, the name is used to determine the getter name and the class name. This is optional; if it isn't present,
     * or the index key isn't in the list, then the index name will be the same as the index key.
     *
     * <p>Would be nice to make this a map but it doesn't seem possible to use maps as attribute values.
     * @return The list of index key/index name pairs
     */
    String[] indexNames() default {};

    /**
     * This is used internally by the processor to track which class we need to process. Do not set it when annotating
     * classes.
     * @return The original class that we need a dynamo table or codec for
     */
    Class<?> deferredFrom() default Object.class;
}
