package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoAttribute;
import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondaryPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondarySortKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Setter
@Getter
@ToString
@EqualsAndHashCode
@DynamoItem
@NoArgsConstructor
@AllArgsConstructor
public class NoSortKey {

    @DynamoPartitionKey
    private String p;

    @DynamoSecondaryPartitionKey("i1")
    private int i1p;

    @DynamoSecondaryPartitionKey("i2")
    private int i2p;

    @DynamoAttribute(value="when", codec=DynamoInstantCodec.class)
    @DynamoSecondarySortKey({"i1", "i2"})
    private Instant date;
}
