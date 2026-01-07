package ai.phast.ctdynamo.tables;

import ai.phast.ctdynamo.annotations.DynamoAttribute;
import ai.phast.ctdynamo.annotations.DynamoIgnore;
import ai.phast.ctdynamo.annotations.DynamoItem;
import ai.phast.ctdynamo.annotations.DynamoPartitionKey;
import ai.phast.ctdynamo.annotations.DynamoSortKey;
import ai.phast.ctdynamo.annotations.DynamoStringSet;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
@DynamoItem
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class WithStringSet {
    @DynamoPartitionKey
    private String partition;

    @DynamoSortKey
    private int sort;

    @DynamoIgnore
    private List<String> stringSet;

    @DynamoAttribute("stringSet")
    @DynamoStringSet()
    public List<String> getStringSetDynamo() {
        var result = stringSet;
        return result == null || result.isEmpty() ? null : result;
    }

    public void setStringSetDynamo(List<String> newStringSet) {
        stringSet = newStringSet;
    }
}
