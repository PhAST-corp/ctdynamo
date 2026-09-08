package ai.phast.ctdynamo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This attribute can be applied to a getter or a member variable to indicate that this is the secondary partition key of a
 * dynamo index. This can be applied together with other dynamo annotations.
 */
@Target({ElementType.FIELD, ElementType.METHOD}) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoSecondaryPartitionKey {

    /**
     * A list of the index names for which this is the partition key
     * @return A list of the index names for which this is the partition key
     */
    String[] value();

    /**
     * When you have multiple partition keys for the index, this tells you the order they will appear in
     * parameter lists. 1-based.
     *
     * <p>There is one entry per index named in {@link #value()}, matched up by position, so an attribute may sit at
     * a different position in each index it belongs to. If this is present it must have exactly the same length as
     * {@link #value()}. The default is an empty array, meaning order 1 in every index.
     *
     * <p>Note that Java's single-element shorthand still applies, so <code>order = 2</code> is a fine way to write
     * <code>order = {2}</code> for an attribute that belongs to just one index.
     * @return The 1-based position of this partition key in each index of {@link #value()}
     */
    int[] order() default {};
}
