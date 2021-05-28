package ai.phast.ctdynamo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Most of DynamoIndex is tested when we test the table that extends it, but some parts are easily tested separately.
 */
public class DynamoIndexTest {

    private final List<String> testValues = List.of(
        "no escapes",
        "comma, escape",
        "backslash\\ escape",
        ",,\\\\",
        "\\\\,,",
        "multi\\, esca,pes"
    );

    @Test
    public void testExclusiveStart_shouldReturnSameString_whenEncodingSingleValue() {
        for (var value: testValues) {
            // Setup
            var builder = new StringBuilder();
            var output = new String[1];

            // Act
            DynamoIndex.appendExclusiveStartValue(builder, value);
            DynamoIndex.splitExclusiveStartValues(output, builder.toString());

            // Verify
            Assertions.assertEquals(value, output[0]);
        }
    }

    @Test
    public void testExclusiveStart_shouldReturnSameStrings_whenEncodingTwoValues() {
        for (var value1: testValues) {
            for (var value2 : testValues) {
                // Setup
                var builder = new StringBuilder();
                var output = new String[2];

                // Act
                DynamoIndex.appendExclusiveStartValue(builder, value1);
                builder.append(',');
                DynamoIndex.appendExclusiveStartValue(builder, value2);
                DynamoIndex.splitExclusiveStartValues(output, builder.toString());

                // Verify
                Assertions.assertEquals(value1, output[0]);
                Assertions.assertEquals(value2, output[1]);
            }
        }
    }

    @Test
    public void testExclusiveStart_shouldReturnSameStrings_whenEncodingThreeValues() {
        for (var value1 : testValues) {
            for (var value2 : testValues) {
                for (var value3 : testValues) {
                    // Setup
                    var builder = new StringBuilder();
                    var output = new String[3];

                    // Act
                    DynamoIndex.appendExclusiveStartValue(builder, value1);
                    builder.append(',');
                    DynamoIndex.appendExclusiveStartValue(builder, value2);
                    builder.append(',');
                    DynamoIndex.appendExclusiveStartValue(builder, value3);
                    DynamoIndex.splitExclusiveStartValues(output, builder.toString());

                    // Verify
                    Assertions.assertEquals(value1, output[0]);
                    Assertions.assertEquals(value2, output[1]);
                    Assertions.assertEquals(value3, output[2]);
                }
            }
        }
    }
    
    @Test
    public void testExclusiveStart_shouldThrow_whenTooFewFields() {
        // Act & Verify
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> DynamoIndex.splitExclusiveStartValues(new String[2], "just one value"));
    }

    @Test
    public void testExclusiveStart_shouldThrow_whenTooManyFields() {
        // Act & Verify
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> DynamoIndex.splitExclusiveStartValues(new String[2], "three,values,present"));
    }
}
