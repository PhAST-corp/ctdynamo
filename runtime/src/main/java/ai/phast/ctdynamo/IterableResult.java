package ai.phast.ctdynamo;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A result from a scan or a query. The whole result may not be available at the start. Dynamo returns pages of results;
 * we start fetching page n+1 as soon as the application starts reading page n. If the application reads to the start of
 * a page before the page is ready, then it will wait until the page becomes available.
 *
 * <p>Applications should use the iterator() or stream() calls to return the data.
 * @param <T> The type of item returned by the operation
 */
public abstract class IterableResult<T> implements Iterable<T> {

    /** The index. Used to decode result objects */
    private final DynamoIndex<T, ?, ?> index;

    /** The maximum number of items that we will return. Negative means unlimited */
    private final int limit;

    /** Count of all items returned by all queries. Includes the items that we threw away if the last request had too many */
    private int numItemsFound;

    /** Count of all items scanned by all queries */
    private int numItemsScanned;

    /** Count of all items returned to the iterator */
    private int numItemsReturned;

    /** The capacity consumed by the operation */
    private final CapacityUsed capacity = new CapacityUsed();

    /**
     * Build a new iterable result
     * @param index The index that the scan or query was performed on
     * @param limit The maximum number of items to return
     */
    IterableResult(DynamoIndex<T, ?, ?> index, int limit) {
        this.index = index;
        this.limit = limit;
    }

    /**
     * Get our index. Package protected; applications should not use this, it is used internally to decode result objects.
     * @return The index
     */
    final DynamoIndex<T, ?, ?> getIndex() {
        return index;
    }

    /**
     * Get an exclusive start key that will continue where this iterator leaves off. This will be null if there are
     * no more items in the query or scan (which will always be the case when you run a query or scan with no limit).
     *
     * <p>You must first get to the end of the iterator or stream, then call this function; if you want to continue from
     * an earlier point, then use the last item you get as your exclusive start, via {@link DynamoIndex#getExclusiveStartKey(Object)}.
     * @return The key that lets you resume this operation where it left off
     * @throws IllegalStateException If this is called before the end of the iterator or stream has been reached
     */
    public abstract String getExclusiveStartKey();

    /**
     * Get the maximum number of items that will be returned
     * @return The maximum number of items returned
     */
    public final int getLimit() {
        return limit;
    }

    /**
     * Return the total number of items returned so far by this query or scan
     * @return The total number of items return so far by this query or scan
     */
    public int getNumItemsReturned() {
        return numItemsReturned;
    }

    /**
     * Add more items returned to the current count
     * @param value The number of items that have been returned by the latest page
     */
    void addNumItemsReturned(int value) {
        numItemsReturned += value;
    }

    /**
     * Return the total number of items that have been scanned so far by this query or scan. When there is no query
     * filter, this will always be equal to getItemsReturned.
     * @return The total number of items that have been scanned so far by this query or scan.
     */
    public int getNumItemsScanned() {
        return numItemsScanned;
    }

    /**
     * Add more items scanned to the current count
     * @param value The number of items that have been scanned in the latest page
     */
    void addNumItemsScanned(int value) {
        numItemsScanned += value;
    }

    /**
     * Return the total number of items that have been found by this query or scan. This is equal to getItemsReturned()
     * in most cases; it will be more if the last page of results exceeded the limit specified.
     * @return The total number of items that have been found by this query or scan
     */
    public int getNumItemsFound() {
        return numItemsFound;
    }

    /**
     * Add more items that were found by the latest page
     * @param value The number of items found in the latest page
     */
    void addNumItemsFound(int value) {
        numItemsFound += value;
    }

    /**
     * Get the capacity consumed by this operation. This is updated on each page, only after the iterator or stream
     * is done will this be the total capacity consumed
     * @return The capacity consumed by this operation
     */
    public CapacityUsed getCapacity() {
        return capacity;
    }

    /**
     * Stream the items returned by the operation
     * @return A stream of the items returned by the operation
     */
    public final Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
