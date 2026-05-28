package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.driver.NormalDriver;
import core.road.VehiclePath;
import util.Vector2D;

public class BusTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testBusCreation() {
        Bus bus = new Bus("B1", createDummyPath(), new NormalDriver());
        assertNotNull(bus);
    }

    @Test
    void testBusInheritance() {
        Bus bus = new Bus("B1", createDummyPath(), new NormalDriver());
        assertTrue(bus instanceof Vehicle);
    }

    @Test
    void testBusProfile() {
        Bus bus = new Bus("B1", createDummyPath(), new NormalDriver());
        assertNotNull(bus.getProfile());
    }
}