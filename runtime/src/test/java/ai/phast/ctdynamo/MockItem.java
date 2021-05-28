package ai.phast.ctdynamo;

import java.util.Objects;

public class MockItem {

    public String partition;

    public String sort;

    public int ival;

    public MockItem(String partition, String sort, int ival) {
        this.partition = partition;
        this.sort = sort;
        this.ival = ival;
    }

    @Override
    public String toString() {
        return("MockItem[" + partition + ", " + sort + ", " + ival + "]");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MockItem)) {
            return false;
        }
        var peer = (MockItem)o;
        return Objects.equals(partition, peer.partition)
            && Objects.equals(sort, peer.sort)
            && (ival == peer.ival);
    }
}
