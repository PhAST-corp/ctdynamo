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
     * nextPageSize is zero
     */
    private CompletableFuture<ResponseT> nextPageResponse;

    /**
     * The iterator for the current page of results
     */
    private Iterator<Map<String, AttributeValue>> currentPageIterator = null;

    /**
     * The exclusive start for the next page when nextPageSize is nonzero. Becomes the exclusive start for the next request
     * once nextPageSize is zero. null means we have reached the end of this data
     */
    private Map<String, AttributeValue> lastItemScanned;

    /** If set, we asynchronously fetch items before they are needed */
    private final boolean prefetch;

    /** The page size to use, or -1 to use as much as possible */
    private int pageSize;

    /** The read limit, or -1 if there is none */
    private int readLimit;

    /** Will the results be filtered? */
    private boolean isFiltered;

    /**
     * How big will our next page fetch be? negative means "unlimited", 0 means "do not do any more fetches", any
     * positive number means to fetch that many rows in the next request.
     */
    private int nextPageSize;

    /**
     * Build a new PageResult
     * @param index The index we queried or scanned
     * @param limit The maximum number of items to return
     * @param prefetch true if we should asynchronously fetch pages before they are needed
     * @param readLimit The limit of the number of items we should read, or -1 if there is no limit
     * @param pageSize The size of each read page, or -1 if we want as much as dynamo will provide
     * @param isFiltered A flag telling us whether or not the results may be filtered - that is, whether or not
     *     reads may be ignored
     */
    PagedResult(DynamoIndex<T, ?, ?> index, int limit, boolean prefetch, int pageSize, int readLimit,
                boolean isFiltered) {
        super(index, limit);
        this.prefetch = prefetch;
        this.pageSize = pageSize;
        this.readLimit = readLimit;
        this.isFiltered = isFiltered;
        // Compute nextPageSize for the first page
        int tempNextPageSize = limit;
        if (tempNextPageSize < 0) {
            // With no limit, we just cut at the page size or (if no page size) the read limit
            tempNextPageSize = (pageSize < 0 ? readLimit : pageSize);
        } else if (isFiltered) {
            // If we are filtered, then assume half the items we find will pass the filter
            tempNextPageSize *= 2;
        }
        if (pageSize >= 0) {
            tempNextPageSize = Math.min(tempNextPageSize, pageSize);
        }
        if (readLimit >= 0) {
            tempNextPageSize = Math.min(tempNextPageSize, readLimit);
        }
        nextPageSize = tempNextPageSize;
    }

    /**
     * This needs to be called after the constructor is done. It starts the fetch of the first page when in async mode
     */
    void init() {
        if (nextPageSize != 0 && prefetch) {
            nextPageResponse = fetchNextPage(null, nextPageSize);
        }
    }

    /**
     * Compute the number of items we want in the next page. 0 means "don't read", anything negative means "read as
     * much as dynamo lets us"
     * @param numRead The numbers of items read in the previous page
     * @param numFound The number of items found (passing the filter) in the previous page
     * @return The number of items to request in the next page
     */
    private int computeNextPageSize(int numRead, int numFound) {
        var numItemsRead = getNumItemsRead();
        var numReadsLeft = readLimit - numItemsRead;
        var numItemsNeeded = getLimit() - getNumItemsReturned();
        if (!isFiltered) {
            // No filtering. Request the number of items needed, possibly cut off by the number of reads left or the
            // page size
            if (numReadsLeft >= 0) {
                numItemsNeeded = numItemsNeeded < 0 ? numReadsLeft : Math.min(numItemsNeeded, numReadsLeft);
            }
            if (pageSize >= 0) {
                numItemsNeeded = numItemsNeeded < 0 ? pageSize : Math.min(numItemsNeeded, pageSize);
            }
            return numItemsNeeded;
        }
        if (pageSize >= 0) {
            // It is specified exactly. Return the page size, possibly limited by the total number of reads left
            return numReadsLeft < 0 ? pageSize : Math.min(pageSize, numReadsLeft);
        }
        if (numItemsNeeded < 0) {
            // No limit to the number of items returned, so return the read limit. If that is negative, then we'll
            // read as much as dynamo lets us.
            return numReadsLeft;
        }
        int result;
        if (numFound == 0) {
            // We found no items in the previous page. Double the page size
            result = numRead * 2;
        } else {
            // numRead / numFound is the ratio of how many items you must scan to find one that passes the filter
            // (assuming that the previous page is representative). We multiply that by the number of items we need
            // to find the page size that should give us the items we need; then we double it to make it almost
            // certain to have the items we need. We first do all multiplies, then add one less than the denominator,
            // to do a proper rounded up integer-based operation
            result = (2 * numItemsNeeded * numRead + numFound - 1) / numFound;
        }
        return numReadsLeft < 0 ? result : Math.min(result, numReadsLeft);
    }

    /**
     * Starts a future that will get the next page of the result. Used only in async mode
     * @param exclusiveStart The start point for the next page. May be null for the first page
     * @param pageSize The number of items to request in the page
     * @return A future that will return the next page of results
     */
    abstract CompletableFuture<ResponseT> fetchNextPage(Map<String, AttributeValue> exclusiveStart, int pageSize);

    /**
     * Fetches the current page of results. Used only in synchronous mode
     * @param exclusiveStart The start point for the current page
     * @param pageSize The number of items to request in the page
     * @return The current page of results
     */
    abstract ResponseT fetchCurrentPage(Map<String, AttributeValue> exclusiveStart, int pageSize);

    /**
     * The "hasNext" function for our iterator. Checks whether we have more data or not. If we are at the end of a
     * page, we will wait for the next page to finish (when we are async) or request the next page and wait for it (when
     * we are synchronous)
     * @return true if there is more data to fetch, false if we are at the end of the query/scan
     */
    private boolean iteratorHasNext() {
        // We have to loop here because if a query filter was used, we could get entire pages back that are empty but
        // yet we still have more data to read. So we do this:
        // 1. Check if our current iterator has data; if so, return true
        // 2. Check if we've set nextPageSize to zero; if so, return false
        // 3. If neither of those was true, then read in the next page, build an iterator around the items in that
        //    page, and loop back to try again. If the page we got back is empty but indicates there is more data
        //    via the exclusive start, then we'll loop back to 3. and get another page
        while (true) {
            if ((currentPageIterator != null) && currentPageIterator.hasNext()) {
                // We have an iterator, it has another element
                return true;
            }
            currentPageIterator = null; // Indicate we do not have a useful iterator
            if (nextPageSize == 0) {
                return false;
            }

            // We have another request coming. If it is non-empty, then we have more data. If we are prefetching, then
            // we get the request by joining with the future; if we are not prefetching, then we make the request now.
            var response = prefetch ? nextPageResponse.join() : fetchCurrentPage(lastItemScanned, nextPageSize);
            var items = getItems(response);
            var numItems = items.size();
            addNumItemsFound(numItems);  // Update this BEFORE we chop out unneeded returned items
            var limit = getLimit();
            lastItemScanned = getLastEvaluatedKey(response);
            if ((limit >= 0) && (numItems + getNumItemsReturned() > limit)) {
                // We got too many items. Chop off the rest.
                numItems = limit - getNumItemsReturned();
                lastItemScanned = items.get(numItems - 1);
                items = items.subList(0, numItems);
            }
            var numRead = getScannedCount(response);

            // Update counters with data from the new request
            addNumItemsRead(numRead);
            addNumItemsReturned(numItems);
            getCapacity().add(getRawCapacity(response));

            nextPageSize = (lastItemScanned == null ? 0 : computeNextPageSize(numRead, numItems));
            currentPageIterator = items.iterator();

            if (prefetch && (nextPageSize != 0)) {
                // Ask for another page
                nextPageResponse = fetchNextPage(lastItemScanned, nextPageSize);
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
        if (nextPageSize > 0) {
            // Not allowed to ask for the exclusive start until we have reached the end
            throw new IllegalStateException("The exclusive start is unknown until the iterator or stream reaches the end");
        }
        return lastItemScanned == null ? null : getIndex().getExclusiveStartKey(lastItemScanned);
    }
}
