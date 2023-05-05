package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

/**
 * Class returned from a call to DynamoTable.updateItem
 * @param <T> The type of item stored in the table
 */
public class UpdateItemResult<T> {

    /** The item returned by the call */
    private final T item;

    /** The amount of capacity consumed by the operation */
    private final double capacity;

    /**
     * Build a new result
     * @param item The item returned
     * @param capacity The capacity consumed
     */
    public UpdateItemResult(T item, ConsumedCapacity capacity) {
        this.item = item;
        var tempCapacity = 0.0;
        if (capacity != null) {
            var capacityUnits = capacity.capacityUnits();
            if (capacityUnits != null) {
                tempCapacity = capacityUnits;
            }
        }
        this.capacity = tempCapacity;
    }

    /**
     * Get the item returned
     * @return The item returned
     */
    public T getItem() {
        return item;
    }

    /**
     * Get the capacity consumed
     * @return The capacity consumed
     */
    public double getCapacity() {
        return capacity;
    }
}
