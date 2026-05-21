package core.vehicle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import util.Vector2D;

class AmbulanceTest {

    @Test
    void shouldBePriorityVehicle() {

        VehiclePath mockPath = new VehiclePath("mock_path", List.of(Vector2D.ZERO, new Vector2D(100, 0)), 0, "light_1", "N", "S");

        Ambulance ambulance =
                new Ambulance("test-ambu", mockPath);

        assertTrue(ambulance instanceof PriorityVehicle);
    }
}