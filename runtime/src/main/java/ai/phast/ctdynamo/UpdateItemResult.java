package ai.phast.ctdynamo;

/**
 * Class returned from a call to DynamoTable.updateItem
 * @param <T> The type of item stored in the table
 */
public class UpdateItemResult<T> {

    /** The item returned by the call */
    private final T item;

    /** The amount of capacity consumed by the operation */
    private final CapacityUsed capacity;

    /**
     * Build a new result
     * @param item The item returned
     * @param capacity The capacity consumed
     */
    public UpdateItemResult(T item, CapacityUsed capacity) {
        this.item = item;
        this.capacity = capacity;
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
    public CapacityUsed getCapacity() {
        return capacity;
    }
}
