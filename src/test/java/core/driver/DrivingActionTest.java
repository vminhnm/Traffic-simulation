package core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class DrivingActionTest {

    @Test
    void testAccelerateAction() {
        DrivingAction action = DrivingAction.ACCELERATE;
        assertEquals("ACCELERATE", action.name());
    }

    @Test
    void testBrakeAction() {
        DrivingAction action = DrivingAction.BRAKE;
        assertEquals("BRAKE", action.name());
    }

    @Test
    void testChangeLaneLeftAction() {
        assertNotNull(DrivingAction.CHANGE_LANE_LEFT);
    }

    @Test
    void testChangeLaneRightAction() {
        assertNotNull(DrivingAction.CHANGE_LANE_RIGHT);
    }
}