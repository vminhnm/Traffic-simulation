package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.driver.NormalDriver;
import core.road.VehiclePath;
import util.Vector2D;

public class TruckTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testTruckCreation() {
        Truck truck = new Truck("T1", createDummyPath(), new NormalDriver());
        assertNotNull(truck);
    }

    @Test
    void testTruckInheritance() {
        Truck truck = new Truck("T1", createDummyPath(), new NormalDriver());
        assertTrue(truck instanceof Vehicle);
    }

    @Test
    void testTruckProfile() {
        Truck truck = new Truck("T1", createDummyPath(), new NormalDriver());
        assertNotNull(truck.getProfile());
    }
}