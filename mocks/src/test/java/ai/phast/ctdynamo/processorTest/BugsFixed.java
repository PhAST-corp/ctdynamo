package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.annotations.DynamoAttribute;
import ai.phast.ctdynamo.annotations.DynamoIgnore;
import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import lombok.Getter;
import lombok.Setter;

/**
 * This table shows bugs that have been fixed. It is here to catch regressions, so that we will know if a previously
 * fixed bug reappears.
 */
@DynamoItem
@Setter
@Getter
public class BugsFixed {

    @DynamoPartitionKey
    private String partitionKey;

    @DynamoIgnore
    private String value1;

    /**
     * There was a bug, if you had an ignored attribute, then overrode another getter to replace the ignored attribute,
     * you would get told that "Two elements of attribute value1 include dynamo annotations." The proper behavior is to
     * ignore the ignored attribute, use the @DynamoAttribute one.
     * @return Our override of value1
     */
    @DynamoAttribute("value1")
    public String getValue1Alternate() {
        return "this is value1";
    }

    public void setValue1Alternate(String value) {
        // Do nothing
    }
}
