package ai.phast.ctdynamo;

import java.util.ArrayList;
import java.util.List;

/**
 * The output of any "extended" get, put, or delete command. This returns the item returned by the non-extended version
 * and the consumed capacity of the operation.
 * @param <T> The type of the item returned
 * @param <UnprocessedT> The type of the unprocessed items returned
 */
public class ExtendedBatchResult<T, UnprocessedT> {

    /** Items returned by the command */
    private final List<T> items = new ArrayList<>();

    /** The unprocessed items */
    private final List<UnprocessedT> unprocessedValues = new ArrayList<>();

    /** The capacity used by the request */
    private final CapacityUsed capacity = new CapacityUsed();

    /** Build a blank batch result */
    public ExtendedBatchResult() {
    }

    /**
     * Get the items returned by dynamo
     * @return A list of items returned by dynamo
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * Get a list of the unprocessed values. These are the values that had no effect during the request
     * @return A list of the unprocessed values
     */
    public List<UnprocessedT> getUnprocessedValues() {
        return unprocessedValues;
    }

    /**
     * Get the capacity consumed by the operation
     * @return The capacity consumed by the operation
     */
    public CapacityUsed getCapacity() {
        return capacity;
    }
}
