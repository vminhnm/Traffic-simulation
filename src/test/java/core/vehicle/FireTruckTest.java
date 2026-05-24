package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.driver.EmergencyDriver;
import core.road.VehiclePath;
import util.Vector2D;

public class FireTruckTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testFireTruckCreation() {
        FireTruck truck = new FireTruck("FT1", createDummyPath(), new EmergencyDriver());
        assertNotNull(truck);
    }

    @Test
    void testPriorityVehicle() {
        FireTruck truck = new FireTruck("FT1", createDummyPath(), new EmergencyDriver());
        assertTrue(truck.isPriorityVehicle());
    }

    @Test
    void testInheritance() {
        FireTruck truck = new FireTruck("FT1", createDummyPath(), new EmergencyDriver());
        assertTrue(truck instanceof Vehicle);
    }
}