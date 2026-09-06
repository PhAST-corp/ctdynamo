package ai.phast.ctdynamo.tables;

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

/**
 * A table with two indexes that use more than one partition key. The fields are deliberately declared out of
 * order, so that a wrong implementation that used declaration order instead of the "order" attribute would fail.
 *
 * <p>The "trio" index has three partition keys of mixed types plus a sort key; "quad" has the maximum four and no
 * sort key. "tenant" belongs to both, at a different position in each.
 */
@Setter
@Getter
@DynamoItem
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MultiPartition {

    @DynamoPartitionKey
    private String id;

    @DynamoSecondaryPartitionKey(value = "trio", order = 3)
    private String category;

    // Sits at position 1 in "trio" but position 2 in "quad" - this is what the per-index order array is for
    @DynamoSecondaryPartitionKey(value = {"trio", "quad"}, order = {1, 2})
    private String tenant;

    @DynamoSecondaryPartitionKey(value = "quad", order = 4)
    private String status;

    @DynamoSecondaryPartitionKey(value = "trio", order = 2)
    private int region;

    // Single-element shorthand still works for an attribute that belongs to one index
    @DynamoSecondaryPartitionKey(value = "quad", order = 1)
    private String team;

    @DynamoSecondaryPartitionKey(value = "quad", order = 3)
    private long shard;

    @DynamoSecondarySortKey("trio")
    private String created;
}
