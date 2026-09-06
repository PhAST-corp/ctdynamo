package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The base class for all global secondary indexes and local secondary indexes. Tables also extend this class
 * @param <T> The data type of item stored in this index
 * @param <Partition1T> The type of the first partition key for this index
 * @param <Partition2T> The type of the second partition key. An index may have up to four partition keys; if it has
 *               fewer than two, then Partition2T will be Void
 * @param <Partition3T> The type of the third partition key, or Void if this index has fewer than three
 * @param <Partition4T> The type of the fourth partition key, or Void if this index has fewer than four
 * @param <SortT> The type of the sort key for this index. Tables do not require sort keys; if this table has none,
 *               then SortT will be Void
 */
public abstract class DynamoIndex<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> {

    /** The name of the table that this index is in */
    private final String tableName;

    /** The name of the index. null if this is a table */
    private final String indexName;

    /** The names of the partition key attributes for this index, in key order. Never empty */
    private final List<String> partitionKeyAttributes;

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
     * @param partitionKeyAttributes The names of the partition key attributes. This should be an immutable list
     * @param sortKeyAttribute The name of the sort key attribute, or null if this is an index with no sort key
     * @throws NullPointerException If both client and asyncClient are null
     */
    public DynamoIndex(DynamoDbClient client, DynamoDbAsyncClient asyncClient,
                       String tableName, String indexName, List<String> partitionKeyAttributes,
                       String sortKeyAttribute) {
        if (client == null && asyncClient == null) {
            throw new NullPointerException("At least one of client or asyncClient must be non-null");
        }
        this.client = client;
        this.asyncClient = asyncClient;
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.indexName = indexName;
        this.partitionKeyAttributes = partitionKeyAttributes;
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
     * Get the names of our partition key attributes, in key order. A table always has exactly one; an index may have
     * up to four. The size of this list is what tells you how many partition values an index expects.
     * @return The names of our partition key attributes, in key order
     */
    protected final List<String> getPartitionKeyAttributes() {
        return partitionKeyAttributes;
    }

    /**
     * Get the name of our sort key attribute
     * @return The name of our sort key attribute, or null if we are a table and have no sort key
     */
    protected final String getSortKeyAttribute() {
        return sortKeyAttribute;
    }

    /**
     * Read the first partition value from an item
     * @param value The item
     * @return The first partition key of item
     */
    public abstract Partition1T getPartitionValue1(T value);

    /**
     * Read the second partition value from an item
     * @param value The item
     * @return The second partition key of item, or null if this index has only one partition key. Callers rely on
     *         that null to work out how many partition keys an index actually has
     */
    public abstract Partition2T getPartitionValue2(T value);

    /**
     * Read the third partition value from an item
     * @param value The item
     * @return The third partition key of item, or null if this index has fewer than three partition keys
     */
    public abstract Partition3T getPartitionValue3(T value);

    /**
     * Read the fourth partition value from an item
     * @param value The item
     * @return The fourth partition key of item, or null if this index has fewer than four partition keys
     */
    public abstract Partition4T getPartitionValue4(T value);

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
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> query() {
        return new Query<>(this);
    }

    /**
     * Start building a query on this table or index. The query will by synchronous, and will search items with the
     * specified partition key
     * @param partition1Value The first partition value that will be searched by this query
     * @return A query for this table or index
     */
    public final Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> query(
            Partition1T partition1Value) {
        return new Query<>(this).partitionValue(partition1Value, null, null, null);
    }

    /**
     * Start building a query on this table or index. The query will by synchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @return A query for this table or index
     */
    public final Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> query(
            Partition1T partition1Value,
            Partition2T partition2Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, null, null);
    }
    /**
     * Start building a query on this table or index. The query will by synchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @param partition3Value The third partition value that will be searched by this query
     * @return A query for this table or index
     */
    public final Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> query(
            Partition1T partition1Value,
            Partition2T partition2Value,
            Partition3T partition3Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, partition3Value, null);
    }
    /**
     * Start building a query on this table or index. The query will by synchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @param partition3Value The third partition value that will be searched by this query
     * @param partition4Value The fourth partition value that will be searched by this query
     * @return A query for this table or index
     */
    public final Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> query(
            Partition1T partition1Value,
            Partition2T partition2Value,
            Partition3T partition3Value,
            Partition4T partition4Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, partition3Value, partition4Value);
    }
    /**
     * Start building a query on this table or index. The query will be asynchronous (but that may be changed by calling
     * async(boolean) on the query returned)
     * @return A query for this table or index
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> queryAsync() {
        return new Query<>(this).async(true);
    }

    /**
     * Start building a query on this table or index. The query will by asynchronous, and will search items with the
     * specified partition key
     * @param partition1Value The first partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> queryAsync(
            Partition1T partition1Value) {
        return new Query<>(this).partitionValue(partition1Value, null, null, null)
                .async(true);
    }

    /**
     * Start building a query on this table or index. The query will by asynchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> queryAsync(
            Partition1T partition1Value, Partition2T partition2Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, null, null)
                .async(true);
    }

    /**
     * Start building a query on this table or index. The query will by asynchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @param partition3Value The third partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> queryAsync(
            Partition1T partition1Value, Partition2T partition2Value, Partition3T partition3Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, partition3Value, null)
                .async(true);
    }

    /**
     * Start building a query on this table or index. The query will by asynchronous, and will search items with the
     * specified partition keys
     * @param partition1Value The first partition value that will be searched by this query
     * @param partition2Value The second partition value that will be searched by this query
     * @param partition3Value The third partition value that will be searched by this query
     * @param partition4Value The fourth partition value that will be searched by this query
     * @return A query for this table or index
     */
    public Query<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT> queryAsync(
            Partition1T partition1Value, Partition2T partition2Value, Partition3T partition3Value,
            Partition4T partition4Value) {
        return new Query<>(this).partitionValue(partition1Value, partition2Value, partition3Value, partition4Value)
                .async(true);
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
     * Convert the first partition value for this index to an AttributeValue
     * @param partitionValue The first partition value
     * @return The equivalent AttributeValue
     */
    protected abstract AttributeValue partitionValue1ToAttributeValue(Partition1T partitionValue);

    /**
     * Convert the second partition value for this index to an AttributeValue
     * @param partitionValue The second partition value
     * @return The equivalent AttributeValue
     * @throws UnsupportedOperationException If this index has fewer than 2 partition keys, in which case
     *         Partition2T is Void and there is no value to convert
     */
    protected abstract AttributeValue partitionValue2ToAttributeValue(Partition2T partitionValue);

    /**
     * Convert the third partition value for this index to an AttributeValue
     * @param partitionValue The third partition value
     * @return The equivalent AttributeValue
     * @throws UnsupportedOperationException If this index has fewer than 3 partition keys, in which case
     *         Partition3T is Void and there is no value to convert
     */
    protected abstract AttributeValue partitionValue3ToAttributeValue(Partition3T partitionValue);

    /**
     * Convert the fourth partition value for this index to an AttributeValue
     * @param partitionValue The fourth partition value
     * @return The equivalent AttributeValue
     * @throws UnsupportedOperationException If this index has fewer than 4 partition keys, in which case
     *         Partition4T is Void and there is no value to convert
     */
    protected abstract AttributeValue partitionValue4ToAttributeValue(Partition4T partitionValue);

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
