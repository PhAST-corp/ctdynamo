package ai.phast.ctdynamo.processorTest;

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

@DynamoItem
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ArrayOuterItem {

    private int[] nums;

    private ArrayInnerItem[] inners;

    private String[] strings;

    private boolean[][] matrix;

    private short[][][] threeD;

    @DynamoPartitionKey
    private int partition;

    @DynamoSecondaryPartitionKey("i")
    private String ind;

    @DynamoSecondarySortKey("i")
    private String sortInd;
}
