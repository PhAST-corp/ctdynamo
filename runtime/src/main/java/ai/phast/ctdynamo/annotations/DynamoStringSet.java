package ai.phast.ctdynamo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This attribute can be applied to a getter or a member variable to indicate that this is a string set.
 */
@Target({ElementType.FIELD, ElementType.METHOD}) @Retention(RetentionPolicy.SOURCE)
public @interface DynamoStringSet {
}
