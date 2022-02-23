package ai.phast.ctdynamo;

import ai.phast.ctdynamo.tables.WithBinary;
import ai.phast.ctdynamo.tables.WithBinaryDynamoCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BinaryTest {

    @Test
    public void testBinary_shouldHaveSameValue_whenEncodedAndDecoded() {
        // Setup
        var expected = new WithBinary();
        expected.setId("id");
        expected.setValue(new byte[] {5, 10, 15, 20, 25, 30});
        var codec = new WithBinaryDynamoCodec();

        // Act
        var encoded = codec.encode(expected);
        var actual = codec.decode(encoded);

        // Verify
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testBinary_shouldHaveSameValue_whenZeroLength() {
        // Setup
        var expected = new WithBinary();
        expected.setId("id");
        expected.setValue(new byte[0]);
        var codec = new WithBinaryDynamoCodec();

        // Act
        var encoded = codec.encode(expected);
        var actual = codec.decode(encoded);

        // Verify
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testBinary_shouldHaveSameValue_whenNull() {
        // Setup
        var expected = new WithBinary();
        expected.setId("id");
        var codec = new WithBinaryDynamoCodec();

        // Act
        var encoded = codec.encode(expected);
        var actual = codec.decode(encoded);

        // Verify
        Assertions.assertEquals(expected, actual);
    }
}
