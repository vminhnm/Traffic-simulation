package util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class IdGeneratorTest {

    @Test
    void testGenerateId() {
        String id = IdGenerator.next("CAR");

        assertNotNull(id);
        assertTrue(id.startsWith("CAR"));
    }

    @Test
    void testUniqueIds() {
        String id1 = IdGenerator.next("BUS");
        String id2 = IdGenerator.next("BUS");

        assertNotEquals(id1, id2);
    }
}