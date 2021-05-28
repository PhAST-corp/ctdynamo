package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

/**
 * The output of any "extended" get, put, or delete command. This returns the item returned by the non-extended version
 * and the consumed capacity of the operation.
 * @param <T> The item returned
 */
public class ExtendedItemResult<T> {

    /** The item returned */
    private final T item;

    /** The capacity used by the operation */
    private final CapacityUsed capacity;

    /**
     * Build a new extended result
     * @param item The item returned
     * @param rawCapacity The capacity consumed
     */
    ExtendedItemResult(T item, ConsumedCapacity rawCapacity) {
        this(item, new CapacityUsed(rawCapacity));
    }

    /**
     * Build a new extended result
     * @param item The item returned
     * @param capacity The capacity consumed
     */
    public ExtendedItemResult(T item, CapacityUsed capacity) {
        this.item = item;
        this.capacity = capacity;
    }

    /**
     * Get the item returned. For a get, this is the item requested; for a put, the item replaced; for a delete, the
     * item removed. For all three, this may be null if no item matched
     * @return The item requested, replaced, or deleted by this operation. Null if no item matched
     */
    public T getItem() {
        return item;
    }

    /**
     * Get the consumed capacity of this operation
     * @return The consumed capacity of this operation
     */
    public CapacityUsed getCapacity() {
        return capacity;
    }
}
