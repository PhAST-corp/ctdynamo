package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSortKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@DynamoItem
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class NoIndex {

    @DynamoPartitionKey
    private String partition;

    @DynamoSortKey
    private int sort;

    private boolean bVal;
}
