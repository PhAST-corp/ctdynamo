package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@DynamoItem(DynamoItem.Output.CODEC)
@Setter
@Getter
@ToString
@EqualsAndHashCode
public class WithBinary {

    @DynamoPartitionKey
    private String id;

    private byte[] value;

}
