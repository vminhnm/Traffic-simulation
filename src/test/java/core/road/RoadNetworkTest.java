package core.road;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class RoadNetworkTest {

    @Test
    void testRoadNetworkCreation() {
        RoadNetwork network = new RoadNetwork();
        assertNotNull(network);
    }
}