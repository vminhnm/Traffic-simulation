package core.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrivingDecisionTest {

    @Test
    void shouldStoreAcceleration() {

        DrivingDecision decision =
                DrivingDecision.accelerate(1.5);

        assertEquals(1.5,
                decision.getTargetSpeed());
    }
}