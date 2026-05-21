package core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class VehicleProfileTest {

    @Test
    void shouldBuildProfile() {

        VehicleProfile profile =
                VehicleProfile.builder("test_car")
                        .defaultMaxSpeed(120)
                        .build();

        assertEquals(120, profile.getDefaultMaxSpeed());
    }
}