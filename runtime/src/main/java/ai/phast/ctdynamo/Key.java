package ai.phast.ctdynamo;

import java.util.Objects;

/**
 * Class that represents a key of a Dynamo table. This is primarily used in batch operations.
 * @param <Partition1T> The type of the first partition key
 * @param <Partition2T> The type of the second partition key, or Void if the index has fewer than two
 * @param <Partition3T> The type of the third partition key, or Void if the index has fewer than three
 * @param <Partition4T> The type of the fourth partition key, or Void if the index has fewer than four
 * @param <SortT> The type of the stort key, or Void if there is no sort key
 */
public final class Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> {

    /** The first partition key */
    private final Partition1T partition1;

    /** The second partition key, or null if this key has fewer than 2 */
    private final Partition2T partition2;

    /** The third partition key, or null if this key has fewer than 3 */
    private final Partition3T partition3;

    /** The fourth partition key, or null if this key has fewer than 4 */
    private final Partition4T partition4;

    /** The sort key, or null if there is no sort key */
    private final SortT sort;

    /**
     * Build a key. The first partition value may not be null; the rest are null exactly when this key has fewer than
     * four partition values. The sort value should only be null if SortT is Void, but that cannot be enforced due to
     * Java's erasure system.
     *
     * <p>The only constructor, and private on purpose: use the {@code of} factories instead. They pick how many
     * partition values you are supplying, infer Void for the rest, and reject nulls among the ones you passed.
     * @param partition1 The first partition value
     * @param partition2 The second partition value, or null if this key has only one
     * @param partition3 The third partition value, or null if this key has fewer than three
     * @param partition4 The fourth partition value, or null if this key has fewer than four
     * @param sort The sort value
     * @throws NullPointerException If partition1 is null
     */
    private Key(Partition1T partition1, Partition2T partition2, Partition3T partition3, Partition4T partition4,
               SortT sort) {
        this.partition1 = Objects.requireNonNull(partition1);
        this.partition2 = partition2;
        this.partition3 = partition3;
        this.partition4 = partition4;
        this.sort = sort;
    }

    /**
     * Build a key with one partition value. The constructor is private because its diamond form,
     * <code>new Key&lt;&gt;(partition, sort)</code>, cannot infer the unused partition types and so fails wherever a
     * <code>Key&lt;P, Void, Void, Void, S&gt;</code> is expected. These factories pin them to Void for you.
     * @param partition1 The partition value
     * @param sort The sort value
     * @param <Partition1T> The type of the partition key
     * @param <SortT> The type of the sort key
     * @return The key
     * @throws NullPointerException If partition1 is null
     */
    public static <Partition1T, SortT> Key<Partition1T, Void, Void, Void, SortT> of(
            Partition1T partition1, SortT sort) {
        return new Key<>(partition1, null, null, null, sort);
    }

    /**
     * Build a key with two partition values
     * @param partition1 The first partition value
     * @param partition2 The second partition value
     * @param sort The sort value
     * @param <Partition1T> The type of the first partition key
     * @param <Partition2T> The type of the second partition key
     * @param <SortT> The type of the sort key
     * @return The key
     * @throws NullPointerException If any partition value is null
     */
    public static <Partition1T, Partition2T, SortT> Key<Partition1T, Partition2T, Void, Void, SortT> of(
            Partition1T partition1, Partition2T partition2, SortT sort) {
        return new Key<>(partition1, Objects.requireNonNull(partition2), null, null, sort);
    }

    /**
     * Build a key with three partition values
     * @param partition1 The first partition value
     * @param partition2 The second partition value
     * @param partition3 The third partition value
     * @param sort The sort value
     * @param <Partition1T> The type of the first partition key
     * @param <Partition2T> The type of the second partition key
     * @param <Partition3T> The type of the third partition key
     * @param <SortT> The type of the sort key
     * @return The key
     * @throws NullPointerException If any partition value is null
     */
    public static <Partition1T, Partition2T, Partition3T, SortT>
        Key<Partition1T, Partition2T, Partition3T, Void, SortT> of(
            Partition1T partition1, Partition2T partition2, Partition3T partition3, SortT sort) {
        return new Key<>(partition1, Objects.requireNonNull(partition2), Objects.requireNonNull(partition3),
            null, sort);
    }

    /**
     * Build a key with four partition values
     * @param partition1 The first partition value
     * @param partition2 The second partition value
     * @param partition3 The third partition value
     * @param partition4 The fourth partition value
     * @param sort The sort value
     * @param <Partition1T> The type of the first partition key
     * @param <Partition2T> The type of the second partition key
     * @param <Partition3T> The type of the third partition key
     * @param <Partition4T> The type of the fourth partition key
     * @param <SortT> The type of the sort key
     * @return The key
     * @throws NullPointerException If any partition value is null
     */
    public static <Partition1T, Partition2T, Partition3T, Partition4T, SortT>
        Key<Partition1T, Partition2T, Partition3T, Partition4T, SortT> of(
            Partition1T partition1, Partition2T partition2, Partition3T partition3, Partition4T partition4,
            SortT sort) {
        return new Key<>(partition1, Objects.requireNonNull(partition2), Objects.requireNonNull(partition3),
            Objects.requireNonNull(partition4), sort);
    }

    /**
     * Get the first partition value
     * @return The first partition value, or null if this key has fewer partition values
     */
    public Partition1T getPartition1() {
        return partition1;
    }

    /**
     * Get the second partition value
     * @return The second partition value, or null if this key has fewer partition values
     */
    public Partition2T getPartition2() {
        return partition2;
    }

    /**
     * Get the third partition value
     * @return The third partition value, or null if this key has fewer partition values
     */
    public Partition3T getPartition3() {
        return partition3;
    }

    /**
     * Get the fourth partition value
     * @return The fourth partition value, or null if this key has fewer partition values
     */
    public Partition4T getPartition4() {
        return partition4;
    }

    /**
     * Get the sort value
     * @return The sort value
     */
    public SortT getSort() {
        return sort;
    }

    @Override
    public int hashCode() {
        var value = partition1.hashCode();
        if (partition2 != null) {
            value = value * 31 + partition2.hashCode();
        }
        if (partition3 != null) {
            value = value * 31 + partition3.hashCode();
        }
        if (partition4 != null) {
            value = value * 31 + partition4.hashCode();
        }
        if (sort != null) {
            value = value * 31 + sort.hashCode();
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof Key) {
            var peer = (Key<?, ?, ?, ?, ?>)other;
            return partition1.equals(peer.partition1)
                    && Objects.equals(partition2, peer.partition2)
                    && Objects.equals(partition3, peer.partition3)
                    && Objects.equals(partition4, peer.partition4)
                    && Objects.equals(sort, peer.sort);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + partition1 + ", "
                + (partition2 == null ? "" : partition2)
                + (partition3 == null ? "" : partition3)
                + (partition4 == null ? "" : partition4)
                + sort + "]";
    }
}
