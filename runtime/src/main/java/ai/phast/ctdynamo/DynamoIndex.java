package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Objects;

/**
 * The base class for all global secondary indexes and local secondary indexes. Tables also extend this class
 * @param <T> The data type of item stored in this index
 * @param <PartitionT> The type of the partition key for this index
 * @param <SortT> The type of the sort key for this index. Tables do not require sort keys; if this table has none,
 *               then SortT will be Void
 */
public abstract class DynamoIndex<T, PartitionT, SortT> {

    /** The name of the table that this index is in */
    private final String tableName;

    /** The name of the index. null if this is a table */
    private final String indexName;

    /** The name of the partition key attribute for this index */
    private final String partitionKeyAttribute;

    /** The name of the sort key attribute for this index. null if this is a table with no sort key */
    private final String sortKeyAttribute;

    /** Our client to connect to DynamoDb */
    private final DynamoDbClient client;

    /** Our async client for connecting to dynamodb */
    private final DynamoDbAsyncClient asyncClient;

    /**
     * Construct an index
     * @param client The client
     * @param asyncClient The async client
     * @param tableName The name of the table
     * @param indexName The name of the index
     * @param partitionKeyAttribute The name of the partition key attribute
     * @param sortKeyAttribute The name of the sort key attribute, or null if this is an index with no sort key
     * @throws NullPointerException If both client and asyncClient are null
     */
    public DynamoIndex(DynamoDbClient client, DynamoDbAsyncClient asyncClient,
                       String tableName, String indexName, String partitionKeyAttribute, String sortKeyAttribute) {
        if (client == null && asyncClient == null) {
            throw new NullPointerException("At least one of client or asyncClient must be non-null");
        }
        this.client = client;
        this.asyncClient = asyncClient;
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.indexName = indexName;
        this.partitionKeyAttribute = partitionKeyAttribute;
        this.sortKeyAttribute = sortKeyAttribute;
    }

    /**
     * Get the name of the table that contains this index
     * @return The name of the table that contains this index
     */
    public final String getTableName() {
        return tableName;
    }

    /**
     * Get the name of this index. This will be null if this is a table.
     * @return The name of this index, or null if we are a table
     */
    public final String getIndexName() {
        return indexName;
    }

    /**
     * Get our synchronous dynamo client. May be null, but one of this or the async client must be non-null
     * @return Our synchronous dynamo client. May be null
     */
    protected final DynamoDbClient getClient() {
        return client;
    }

    /**
     * Get our async dynamo client. May be null, but one of this or the synchronous client must be non-null
     * @return Our async client. May be null
     */
    protected final DynamoDbAsyncClient getAsyncClient() {
        return asyncClient;
    }

    /**
     * Get the name of our partition key attribute
     * @return The name of our partition key attribute
     */
    protected final String getPartitionKeyAttribute() {
        return partitionKeyAttribute;
    }

    /**
     * Get the name of our sort key attribute
     * @return The name of our sort key attribute, or null if we are a table and have no sort key
     */
    protected final String getSortKeyAttribute() {
        return sortKeyAttribute;
    }

    /**
     * Read the partition value from an item
     * @param value The item
     * @return The partition key of item
     */
    public abstract PartitionT getPartitionValue(T value);

    /**
     * Read the sort value from an item
     * @param value The item
     * @return The sort key of the item, or null if this is a table with no sort key
     */
    public abstract SortT getSortValue(T value);

    /**
     * Start building a query on this table or index. The query will by synchronous (but that may be changed by
     * calling async(boolean) on the query returned)
     * @return A query for this table or index
     */
    public Query<T, PartitionT, SortT> query() {
        return new Query<>(this);
    }

    /**
     * Start building a query on this table or index. The query will by synchronous, and will search items with the
     * specified partition key
     * @param partitionValue The partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, PartitionT, SortT> query(PartitionT partitionValue) {
        return new Query<>(this).partitionValue(partitionValue);
    }

    /**
     * Start building a query on this table or index. The query will be asynchronous (but that may be changed by calling
     * async(boolean) on the query returned)
     * @return A query for this table or index
     */
    public Query<T, PartitionT, SortT> queryAsync() {
        return new Query<>(this).async(true);
    }

    /**
     * Start building a query on this table or index. The query will by asynchronous, and will search items with the
     * specified partition key
     * @param partitionValue The partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, PartitionT, SortT> queryAsync(PartitionT partitionValue) {
        return new Query<>(this).partitionValue(partitionValue).async(true);
    }

    /**
     * Start building a scan on this table or index. The scan will be synchronous
     * @return A scan for this table or index
     */
    public final Scan<T> scan() {
        return new Scan<>(this);
    }

    /**
     * Start building a scan on this table or index. The scan will be asynchronous
     * @return A scan for this table or index
     */
    public final Scan<T> scanAsync() {
        return new Scan<>(this).async(true);
    }

    /**
     * Convert a partition value for this index to an AttributeValue
     * @param partitionValue The partition value
     * @return The equivalent AttributeValue
     */
    protected abstract AttributeValue partitionValueToAttributeValue(PartitionT partitionValue);

    /**
     * Convert a sort value for this index to an AttributeValue
     * @param sortValue The sort value, or null if this is a table that has no sort key
     * @return The equilavent AttributeValue, or null if this is a table that has no sort key
     */
    protected abstract AttributeValue sortValueToAttributeValue(SortT sortValue);

    /**
     * Convert an item to an attribute value map
     * @param value An item
     * @return An equivalent attribute value map
     */
    protected abstract Map<String, AttributeValue> encode(T value);

    /**
     * Convert an attribute value map to an item
     * @param map The attribute value map
     * @return An equivalent item
     */
    protected abstract T decode(Map<String, AttributeValue> map);

    /**
     * Convert this value into an exclusive start string usable in queries and scans of this table or index. Usually
     * the query/scan itself will tell you the exclusive start, but if you want to stop reading early for some reason
     * and resume where you left off, then this becomes useful.
     * @param value The object we want to start our query or scan from
     * @return The value converted to an exclusive start
     */
    public final String getExclusiveStartKey(T value) {
        return getExclusiveStartKey(encode(value));
    }

    /**
     * Convert a raw dynamo form of an object into an exclusive start value. This takes out the values needed for
     * the exclusive start then packs them into a comma-separated string.
     * @param map The item to turn into an exclusive start
     * @return The item packeg into a string
     */
    protected abstract String getExclusiveStartKey(Map<String, AttributeValue> map);

    /**
     * Convert a string-form exclusive start back into an attribute value map that we can pass to dynamo.
     * @param exclusiveStart Our exclusive start as a string
     * @return The start in dynamo form
     */
    protected abstract Map<String, AttributeValue> decodeExclusiveStart(String exclusiveStart);

    /**
     * Appends a string to a string builder, escaping commas with backslashes.
     * @param builder Where to append the value
     * @param value The value to append
     */
    protected static void appendExclusiveStartValue(StringBuilder builder, String value) {
        int valueLen = value.length();
        int pos = 0;
        for (var i = 0; i < valueLen; ++i) {
            var ch = value.charAt(i);
            if ((ch == ',') || (ch == '\\')) {
                builder.append(value, pos, i).append('\\');
                pos = i;
            }
        }
        builder.append(value, pos, valueLen);
    }

    /**
     * Break apart an exclusive start into strings that can be wrapped in attribute values
     * @param result Where to store the strings
     * @param exclusiveStart The exclusive start string
     * @throws IllegalArgumentException If the number of keys in the start doesn't match the length of the result
     */
    protected static void splitExclusiveStartValues(String[] result, String exclusiveStart) {
        var startLen = exclusiveStart.length();
        var keyNum = 0;
        var builder = new StringBuilder();
        try {
            for (var i = 0; i < startLen; ++i) {
                var ch = exclusiveStart.charAt(i);
                if (ch == ',') {
                    result[keyNum++] = builder.toString();
                    builder.setLength(0);
                } else {
                    builder.append(ch == '\\' ? exclusiveStart.charAt(++i) : ch);
                }
            }
            result[keyNum++] = builder.toString();
            if (keyNum == result.length) {
                // We return on success. If the length doesn't match, we fall out of this try/catch to throw IllegalArgumentException
                return;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // We had too many keys in the string! Increment the count, then fall through to throw the IllegalArgumentException.
            ++keyNum;
        }
        throw new IllegalArgumentException("Expected " + result.length + " keys, got " + keyNum + ": " + exclusiveStart);
    }
}
