package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/**
 * Usually tables are made by the annotation processor. To test table logic without the processor, we need to make one by hand.
 */
public class MockTable extends DynamoTable<MockItem, String, String> {

    /**
     * Construct a new dynamo table.
     *
     * @param client                Our dynamo client (may be null)
     * @param asyncClient           Our async dynamo client (may be null)
     * @param tableName             The name of this table
     * @throws NullPointerException If both client and asyncClient are null
     */
    public MockTable(DynamoDbClient client, DynamoDbAsyncClient asyncClient, String tableName) {
        super(client, asyncClient, tableName, "partition", "sort");
    }

    @Override
    public String getPartitionValue1(MockItem value) {
        return value.partition;
    }

    @Override
    public String getSortValue(MockItem value) {
        return value.sort;
    }

    @Override
    protected AttributeValue partitionValue1ToAttributeValue(String partitionValue) {
        return DynamoTableTest.av(partitionValue);
    }

    @Override
    protected AttributeValue sortValueToAttributeValue(String sortValue) {
        return DynamoTableTest.av(sortValue);
    }

    @Override
    protected Map<String, AttributeValue> encode(MockItem value) {
        return Map.of(
            "partition", DynamoTableTest.av(value.partition),
            "sort", DynamoTableTest.av(value.sort),
            "ival", DynamoTableTest.av(value.ival),
            "stringSet", AttributeValue.fromSs(value.stringSet));
    }

    @Override
    protected MockItem decode(Map<String, AttributeValue> map) {
        return new MockItem(map.get("partition").s(), map.get("sort").s(), Integer.parseInt(map.get("ival").n()));
    }

    @Override
    protected String getExclusiveStartKey(Map<String, AttributeValue> map) {
        var builder = new StringBuilder();
        appendExclusiveStartValue(builder, map.get("partition").s());
        builder.append(',');
        appendExclusiveStartValue(builder, map.get("sort").s());
        return builder.toString();
    }

    @Override
    protected Map<String, AttributeValue> decodeExclusiveStart(String exclusiveStart) {
        var strs = new String[2];
        splitExclusiveStartValues(strs, exclusiveStart);
        return Map.of("partition", AttributeValue.builder().s(strs[0]).build(),
            "sort", AttributeValue.builder().s(strs[1]).build());
    }

    @Override
    public <SecondaryPartition1T, SecondaryPartition2T, SecondaryPartition3T, SecondaryPartition4T, SecondarySortT>
    DynamoIndex<MockItem, SecondaryPartition1T, SecondaryPartition2T, SecondaryPartition3T, SecondaryPartition4T,
        SecondarySortT> getIndex(String name, Class<SecondaryPartition1T> secondaryPartition1Class,
        Class<SecondaryPartition2T> secondaryPartition2Class, Class<SecondaryPartition3T> secondaryPartition3Class,
        Class<SecondaryPartition4T> secondaryPartition4Class, Class<SecondarySortT> secondarySortClass) {
        return null;
    }

    @Override
    protected String getPartitionValue1(AttributeValue value) {
        return value.s();
    }

    @Override
    protected String getSortValue(AttributeValue value) {
        return value.s();
    }
}
