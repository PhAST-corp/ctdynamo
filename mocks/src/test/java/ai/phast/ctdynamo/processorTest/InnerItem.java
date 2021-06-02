package ai.phast.ctdynamo.processorTest;

import ai.phast.ctdynamo.annotations.DynamoAttribute;
import ai.phast.ctdynamo.annotations.DynamoItem;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Set;

/**
 * Test of processor. This tests inner (codec) items, and also includes a group of different data structures that
 * exercise different processor rules.
 */
@DynamoItem(DynamoItem.Output.CODEC)
@Setter
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class InnerItem {

    public enum Color {
        RED, GREEN, BLUE, MAUVE
    }

    private Color color;

    @DynamoAttribute(".-str@nge")
    private List<Color> colorList;

    private List<Integer> intList;

    private List<Boolean> boolList;

    private Set<String> stringSet;

    private List<String> stringList;
}
