package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@DynamoItem(DynamoItem.Output.CODEC)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class CodecWithCodecList {

    @DynamoPartitionKey
    private String partitionKey;

    // There was a bug that in code classes with a list of a class that itself was a codec, the codec variable would not
    // be built correctly. This class will not produce a compileable codec class if that bug is present.
    private List<InnerItem> innerItemList;

}
