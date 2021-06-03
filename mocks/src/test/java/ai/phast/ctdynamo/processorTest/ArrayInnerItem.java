package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.annotations.DynamoItem;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@DynamoItem(DynamoItem.Output.CODEC)
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ArrayInnerItem {

    private double[] doubs;

    private Integer[] ints;
}
