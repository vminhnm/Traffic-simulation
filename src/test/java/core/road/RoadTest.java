package core.road;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

public class RoadTest {

    @Test
    void testRoadCreation() {
        Road road = new Road();
        assertNotNull(road);
    }

    @Test
    void testRoadDirection() {
        Road road = new Road();
        assertNull(road.getDirection());
    }
}