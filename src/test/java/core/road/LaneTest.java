package core.road;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class LaneTest {

    @Test
    void testLaneCreation() {
        Lane lane = new Lane();
        assertNotNull(lane);
    }
}