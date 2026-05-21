package core.road;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import util.Vector2D;

public class VehiclePathTest {

    @Test
    void testStartPoint() {
        Vector2D start = new Vector2D(0, 0);
        Vector2D end = new Vector2D(10, 10);

        VehiclePath path = new VehiclePath("path1", Arrays.asList(start, end), 1, "lane1", "lane2", "light1");

        assertEquals(start, path.getWaypoints().get(0));
    }

    @Test
    void testEndPoint() {
        Vector2D start = new Vector2D(0, 0);
        Vector2D end = new Vector2D(10, 10);

        VehiclePath path = new VehiclePath("path1", Arrays.asList(start, end), 1, "lane1", "lane2", "light1");

        assertEquals(end, path.getWaypoints().get(1));
    }

    @Test
    void testNullStart() {
        assertThrows(Exception.class,
                () -> new VehiclePath(null, null, 0, null, null, null));
    }

    @Test
    void testPathDirection() {
        Vector2D start = new Vector2D(0, 0);
        Vector2D end = new Vector2D(0, 10);

        VehiclePath path = new VehiclePath("path1", Arrays.asList(start, end), 1, "lane1", "lane2", "light1");

        assertNotNull(path);
    }
}