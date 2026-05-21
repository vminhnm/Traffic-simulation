package core.road;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import util.Vector2D;

class VehiclePathTest {

    @Test
    void shouldStoreStartAndEnd() {

        Vector2D start = new Vector2D(0, 0);
        Vector2D end = new Vector2D(10, 10);

        VehiclePath path =
                new VehiclePath("test_path", List.of(start, end), 0, "light_1", "N", "S");

        assertEquals(start, path.getStartPosition());
        assertEquals(end, path.getEndPosition());
    }
}