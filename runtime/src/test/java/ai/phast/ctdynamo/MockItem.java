package ai.phast.ctdynamo;

import java.util.List;
import java.util.Objects;

public class MockItem {

    public String partition;

    public String sort;

    public int ival;

    public List<String> stringSet;

    public MockItem(String partition, String sort, int ival) {
        this(partition, sort, ival, List.of());
    }

    public MockItem(String partition, String sort, int ival, List<String> stringSet) {
        this.partition = partition;
        this.sort = sort;
        this.ival = ival;
        this.stringSet = stringSet;
    }

    @Override
    public String toString() {
        return("MockItem[" + partition + ", " + sort + ", " + ival + ", " + stringSet.toString() + "]");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MockItem)) {
            return false;
        }
        var peer = (MockItem)o;
        return Objects.equals(partition, peer.partition)
            && Objects.equals(sort, peer.sort)
            && (ival == peer.ival)
            && Objects.equals(stringSet, peer.stringSet);
    }
}
