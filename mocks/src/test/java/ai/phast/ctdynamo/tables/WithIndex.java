package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondaryPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSecondarySortKey;
import ai.phast.ctdynamo.annotations.DynamoSortKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@DynamoItem
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class WithIndex {

    @DynamoPartitionKey
    private String p;

    @DynamoSortKey
    private String s;

    @DynamoSecondaryPartitionKey("i")
    private String ip;

    @DynamoSecondarySortKey("i")
    private String is;

}
