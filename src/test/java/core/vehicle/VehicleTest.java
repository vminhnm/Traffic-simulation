package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import core.simulation.SimulationWorld;
import util.Vector2D;

public class VehicleTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testVehicleId() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        assertNotNull(vehicle.getId());
    }

    @Test
    void testVehicleProfile() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        assertNotNull(vehicle.getProfile());
    }

    @Test
    void testVehicleSpeed() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        assertTrue(vehicle.getSpeed() >= 0);
    }

    @Test
    void testVehicleUpdate() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        assertDoesNotThrow(() -> vehicle.update(0.16, new SimulationWorld()));
    }

    @Test
    void testVehicleRenderableState() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        assertNotNull(vehicle.toRenderableState());
    }
}