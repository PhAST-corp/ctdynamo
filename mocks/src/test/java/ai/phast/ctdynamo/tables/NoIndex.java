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

    /** Should be ignored by codec generator, will throw if it isn't */
    private String getShouldIgnore() {
        throw new RuntimeException();
    }

    /** Should be ignored by codec generator, will throw if it isn't */
    private void setShouldIgnore(String value) {
        throw new RuntimeException();
    }

    /** Should be ignored by codec generator, will throw if it isn't */
    public static String getAlsoIgnore() {
        throw new RuntimeException();
    }

    /** Should be ignored by codec generator, will throw if it isn't */
    public static void setAlsoIgnore(String value) {
        throw new RuntimeException();
    }
}
