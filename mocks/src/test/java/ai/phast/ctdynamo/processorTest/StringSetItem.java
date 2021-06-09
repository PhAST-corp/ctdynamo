package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoStringSet;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;
import java.util.List;

@DynamoItem
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class StringSetItem {

    @DynamoPartitionKey
    private String partition;

    @DynamoStringSet
    private Set<String> stringSet;

    private Set<String> stringSetNoAnnotation;

    @DynamoStringSet
    private List<String> stringList;

    private List<String> stringListNoAnnotation;

}
