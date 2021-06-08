package ai.phast.ctdynamo;

import software.amazon.awssdk.services.dynamodb.model.Capacity;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A class that holds the capacity consumed by an operation. This cleans up the raw dynamo capacity object by
 * making values non-nullable; when ctdynamo requests capacity data, it always requests all of it, so there is no
 * need for values to be nullable.
 */
public class CapacityUsed {

    /** Returned instead of null when we have no index data */
    private static final Map<String, ReadWrite> EMPTY_INDEXES = Collections.emptyMap();

    /** The total read capacity consumed by this request, including the table and all indexes */
    private double totalRead;

    /** The total write capacity consumed by this request, including the table and all indexes */
    private double totalWrite;

    /** The read capacity consumed by the table for this request */
    private double tableRead;

    /** The write capacity consumed by the table for this request */
    private double tableWrite;

    /** A map from index name to read and write capacity consumed by that index */
    private Map<String, ReadWrite> indexes;

    /**
     * Default constructor. Gives us all zeroes.
     */
    public CapacityUsed() {
    }

    /**
     * Constructor with vanlues provided
     * @param totalRead Total read value
     * @param totalWrite Total write value
     * @param tableRead Table read value
     * @param tableWrite Table write value
     * @param indexes Optional map of index name to index capacity data
     */
    public CapacityUsed(double totalRead, double totalWrite, double tableRead, double tableWrite, Map<String, ReadWrite> indexes) {
        this.totalRead = totalRead;
        this.totalWrite = totalWrite;
        this.tableRead = tableRead;
        this.tableWrite = tableWrite;
        this.indexes = (indexes == null ? null : new HashMap<>(indexes));
    }

    /**
     * Constructor based on a raw capacity.
     * @param raw The capacity from Dynamo.
     */
    CapacityUsed(ConsumedCapacity raw) {
        add(raw);
    }

    /**
     * The total read capacity consumed by this request, including the table and all indexes
     * @return The total read capacity consumed by this request, including the table and all indexes
     */
    public double getTotalRead() {
        return totalRead;
    }

    /**
     * The total write capacity consumed by this request, including the table and all indexes
     * @return The total read capacity consumed by this request, including the table and all indexes
     */
    public double getTotalWrite() {
        return totalWrite;
    }

    /**
     * The read capacity consumed on the table by this request
     * @return The read capacity consumed on the table by this request
     */
    public double getTableRead() {
        return tableRead;
    }

    /**
     * The write capacity consumed on the table by this request
     * @return The total write capacity consumed on the table by this request
     */
    public double getTableWrite() {
        return tableWrite;
    }

    /**
     * The read and write capacity consumed by indexes. If indexes were not used, this will return an empty map
     * @return A map from index name to read and write capacity consumed
     */
    public Map<String, ReadWrite> getIndexes() {
        return indexes == null ? EMPTY_INDEXES : indexes;
    }

    /**
     * Add this CapacityUsed and the given CapacityUsed together,
     * making this object into the sum of both.
     * @param other The other capacity to add
     */
    public void add(CapacityUsed other) {
        this.tableRead += other.tableRead;
        this.tableWrite += other.tableWrite;
        this.totalRead += other.totalRead;
        this.totalWrite += other.totalWrite;

        for (var key : other.indexes.keySet()) {
            this.indexes.merge(key, other.indexes.get(key),
                (a, b) -> new ReadWrite(a.read + b.read, a.write + b.write));
        }
    }

    /**
     * Add a raw dynamo capacity to this capacity object
     * @param raw The raw dynamo capacity
     */
    void add(ConsumedCapacity raw) {
        if (raw == null) {
            return;
        }
        totalRead += zeroNull(raw.readCapacityUnits());
        totalWrite += zeroNull(raw.writeCapacityUnits());
        var table = raw.table();
        if (table != null) {
            tableRead += zeroNull(table.readCapacityUnits());
            tableWrite += zeroNull(table.writeCapacityUnits());
        }
        if (raw.hasGlobalSecondaryIndexes()) {
            indexes = updateIndexes(indexes, raw.globalSecondaryIndexes());
        }
        if (raw.hasLocalSecondaryIndexes()) {
            indexes = updateIndexes(indexes, raw.localSecondaryIndexes());
        }
    }

    /**
     * Update our indexes based on an the indexes from a raw dynamo capacity object
     * @param indexes Our read/write capacity map
     * @param rawIndexes The read/write capacity map from the raw dynamo object
     * @return Our read/write capacity map after the update
     */
    private static Map<String, ReadWrite> updateIndexes(Map<String, ReadWrite> indexes, Map<String, Capacity> rawIndexes) {
        if (indexes == null) {
            indexes = new HashMap<>();
        }
        for (var indexName: rawIndexes.keySet()) {
            indexes.computeIfAbsent(indexName, key -> new ReadWrite()).add(rawIndexes.get(indexName));
        }
        return indexes;
    }

    /**
     * Null-safe reader. Turns a boxed double into a plain double, converting null to 0.0
     * @param x The boxed double
     * @return The plain double value
     */
    private static double zeroNull(Double x) {
        return x == null ? 0.0 : x;
    }

    @Override
    public String toString() {
        return("CapacityUsed[total=" + totalRead + "," + totalWrite
            + ", table=" + tableRead + "," + tableWrite
            + (indexes == null ? "]" : ", indexes=" + indexes + "]"));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CapacityUsed)) {
            return false;
        }
        var peer = (CapacityUsed)o;
        return (totalRead == peer.totalRead) && (totalWrite == peer.totalWrite)
            && (tableRead == peer.tableRead) && (tableWrite == peer.tableWrite)
            && Objects.equals(indexes, peer.indexes);
    }

    /**
     * The capacity consumed on an index
     */
    public static class ReadWrite {

        /** The read capacity consumed */
        private double read;

        /** The write capacity consumed */
        private double write;

        /**
         * Constructor with values
         * @param read Read capacity value
         * @param write Write capacity value
         */
        public ReadWrite(double read, double write) {
            this.read = read;
            this.write = write;
        }

        /**
         * Default constructor. Returns zero read and write.
         */
        public ReadWrite() {
        }

        /**
         * Add a raw dynamo capacity value to this
         * @param capacity The capacity reported by dynamo
         */
        void add(Capacity capacity) {
            read += zeroNull(capacity.readCapacityUnits());
            write += zeroNull(capacity.writeCapacityUnits());
        }

        /**
         * The read capacity consumed on this index
         * @return The read capacity consumed on this index
         */
        public double getRead() {
            return read;
        }

        /**
         * The write capacity consumed on this index
         * @return The write capacity consumed on this index
         */
        public double getWrite() {
            return write;
        }

        @Override
        public String toString() {
            return("ReadWrite[" + read + ", " + write + "]");
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReadWrite)) {
                return false;
            }
            var peer = (ReadWrite)o;
            return (read == peer.read) && (write == peer.write);
        }
    }
}
