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
    private final double capacity;

    /**
     * Build a new extended result
     * @param item The item returned
     * @param rawCapacity The capacity consumed
     */
    ExtendedItemResult(T item, ConsumedCapacity rawCapacity) {
        this.item = item;
        var tempCapacity = 0.0;
        if (rawCapacity != null) {
            var capacityUnits = rawCapacity.capacityUnits();
            if (capacityUnits != null) {
                tempCapacity = capacityUnits;
            }
        }
        capacity = tempCapacity;
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
    public double getCapacity() {
        return capacity;
    }
}
