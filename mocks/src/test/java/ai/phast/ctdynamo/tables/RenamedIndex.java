package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondaryPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondarySortKey;
import lombok.Getter;
import lombok.Setter;

@DynamoItem(indexNames = {"color5", "color"})
@Setter
@Getter
public class RenamedIndex {

    @DynamoPartitionKey
    private String name;

    @DynamoSecondaryPartitionKey("color5")
    private String hue;

    @DynamoSecondarySortKey("color5")
    private float mass;

    private int value;
}
