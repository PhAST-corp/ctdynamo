package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This extends IterableResult to handle the paging. It exists mostly to hide the ResponseT parameter from the
 * ctDynamo users. It is package protected so that the users will see only the IterableResult class, which is a
 * simpler typespec.
 *
 * @param <T> The type of item returned by the scan
 * @param <ResponseT> The type of dynamo response that this PaginatedResult processes. This is not relevant to the
 *                   application.
 */
abstract class PagedResult<T, ResponseT> extends IterableResult<T> {

    /**
     * When in async mode, this is the future for the next page of results. This will never be null until after
     * endOfData is set
     */
    private CompletableFuture<ResponseT> nextPageResponse;

    /**
     * The iterator for the current page of results
     */
    private Iterator<Map<String, AttributeValue>> currentPageIterator = null;

    /**
     * The exclusive start for the next page when endOfData is false. Becomes the exclusive start for the next request
     * once endOfData is true. null means we have reached the end of this data
     */
    private Map<String, AttributeValue> lastItemScanned;

    /** If set, we asynchronously fetch items before they are needed */
    private final boolean prefetch;

    /** If true, we have read a batch that indicates that there is no more data from the query or scan */
    private boolean endOfData;

    /** If true, we only read in one page. Otherwise, we read all pages. */
    private final boolean onePageLimit;

    /**
     * Build a new PageResult
     * @param index The index we queried or scanned
     * @param limit The maximum number of items to return
     * @param prefetch true if we should asynchronously fetch pages before they are needed
     * @param onePageLimit Should the results be limited to one page?
     */
    PagedResult(DynamoIndex<T, ?, ?> index, int limit, boolean prefetch, boolean onePageLimit) {
        super(index, limit);
        this.prefetch = prefetch;
        this.onePageLimit = onePageLimit;
    }

    /**
     * This needs to be called after the constructor is done. It starts the fetch of the first page when in async mode
     */
    void init() {
        if (getLimit() == 0) {
            // Limit 0 is a special case. We never bother to do a request then. Mark exclusiveStart null and leave
            // futureResponse null so we know there is no more data.
            endOfData = true;
        } else if (prefetch) {
            nextPageResponse = fetchNextPage(null);
        }
    }

    /**
     * Starts a future that will get the next page of the result. Used only in async mode
     * @param exclusiveStart The start point for the next page. May be null for the first page
     * @return A future that will return the next page of results
     */
    abstract CompletableFuture<ResponseT> fetchNextPage(Map<String, AttributeValue> exclusiveStart);

    /**
     * Fetches the current page of results. Used only in synchronous mode
     * @param exclusiveStart The start point for the current page
     * @return The current page of results
     */
    abstract ResponseT fetchCurrentPage(Map<String, AttributeValue> exclusiveStart);

    /**
     * The "hasNext" function for our iterator. Checks whether we have more data or not. If we are at the end of a
     * page, we will wait for the next page to finish (when we are async) or request the next page and wait for it (when
     * we are synchronous)
     * If onePageLimit is true, we only iterate over one page
     * @return true if there is more data to fetch, false if we are at the end of the query/scan
     */
    private boolean iteratorHasNext() {
        // We have to loop here because if a query filter was used, we could get entire pages back that are empty but
        // yet we still have more data to read. So we do this:
        // 1. Check if our current iterator has data; if so, return true.
        // 2. If the current iterator exists, but has no data, this means the page is empty. If we
        //    have a one-page limit, we return false
        // 3. Check if we've marked the "endOfData" flag; if so, return false
        // 4. If neither of those was true, then read in the next page, build an iterator around the items in that
        //    page, and loop back to try again. If the page we got back is empty but indicates there is more data
        //    via the exclusive start, then we'll loop back to 3. and get another page
        while (true) {
            if (currentPageIterator != null) {
                if (currentPageIterator.hasNext()) {
                    // We have an iterator, it has another element
                    return true;
                } else if (onePageLimit) {
                    // We are only reading one page, and we had an iterator, but it ran out.
                    endOfData = true;
                    return false;
                }
            }
            currentPageIterator = null; // Indicate we do not have a useful iterator
            if (endOfData) {
                return false;
            }

            // We have another request coming. If it is non-empty, then we have more data. If we are prefetching, then
            // we get the request by joining with the future; if we are not prefetching, then we make the request now.
            var response = prefetch ? nextPageResponse.join() : fetchCurrentPage(lastItemScanned);

            // Update counters with data from the new request
            var items = getItems(response);
            addNumItemsFound(items.size());
            addNumItemsScanned(getScannedCount(response));
            getCapacity().add(getRawCapacity(response));

            lastItemScanned = getLastEvaluatedKey(response);
            if (lastItemScanned == null) {
                endOfData = true;
            }
            var numItems = items.size();
            if ((getLimit() >= 0) && (numItems + getNumItemsReturned() >= getLimit())) {
                // This page completes the operation by reaching (or exceeding) our limit. Drop the excess data if there
                // is any and mark us as done
                endOfData = true;
                if (numItems + getNumItemsReturned() > getLimit()) {
                    // Chop off the tail of our list.
                    numItems = getLimit() - getNumItemsReturned();
                    lastItemScanned = items.get(numItems - 1);
                    items = items.subList(0, numItems);
                }
            }
            addNumItemsReturned(numItems);
            currentPageIterator = items.iterator();

            if (prefetch && !endOfData) {
                // Ask for another page
                nextPageResponse = fetchNextPage(lastItemScanned);
            }
        }
    }

    /**
     * The "next" function of our iterator. Assumes that hasNext() has been called and returned true
     * @return The next item in the query/scan
     */
    private T iteratorNext() {
        return getIndex().decode(currentPageIterator.next());
    }

    /**
     * Extract the number of items scanned from the response.
     * @param response The response object from a query or scan
     * @return The number of items scanned
     */
    abstract int getScannedCount(ResponseT response);

    /**
     * Get the dynamodb capacity object from the response
     * @param response The response object from a query or scan
     * @return The capacity consumed by the request, or null if the capacity was not returned
     */
    abstract ConsumedCapacity getRawCapacity(ResponseT response);

    /**
     * Get the last evaluated key from the response
     * @param response The response object from a query or scan
     * @return The last evaluated key from the request, or null if the request ended early because it ran out of data
     */
    abstract Map<String, AttributeValue> getLastEvaluatedKey(ResponseT response);

    /**
     * Get the items returned from the response
     * @param response The response object from the query or scan
     * @return The items returned by the query
     */
    abstract List<Map<String, AttributeValue>> getItems(ResponseT response);

    /**
     * Build an iterator over the operation
     * @return An iterator from the query or scan
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iteratorHasNext();
            }

            @Override
            public T next() {
                return iteratorNext();
            }
        };
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
    public String getExclusiveStartKey() {
        if (!endOfData) {
            // Not allowed to ask for the exclusive start until we have reached the end
            throw new IllegalStateException("The exclusive start is unknown until the iterator or stream reaches the end");
        }
        return lastItemScanned == null ? null : getIndex().getExclusiveStartKey(lastItemScanned);
    }
}
