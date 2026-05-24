package core.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class TrafficControllerTest {

    @Test
    void testControllerCreation() {
        TrafficController controller = new TrafficController();
        assertNotNull(controller);
    }
}