package ai.phast.ctdynamo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This attribute can be applied to a getter or a member variable to indicate that this is the secondary sort key of a
 * dynamo index. This can be applied together with other dynamo annotations.
 */
@Target({ElementType.FIELD, ElementType.METHOD}) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoSecondarySortKey {

    /**
     * A list of the index names for which this is the sort key
     * @return A list of the index names for which this is the sort key
     */
    String[] value();

}
