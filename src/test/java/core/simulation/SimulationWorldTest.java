package core.simulation;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import core.vehicle.Vehicle;
import core.vehicle.VehicleFactory;
import util.Vector2D;

public class SimulationWorldTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testCreateWorld() {
        SimulationWorld world = new SimulationWorld();

        assertNotNull(world);
    }

    @Test
    void testAddVehicle() {
        SimulationWorld world = new SimulationWorld();
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());

        world.addVehicle(vehicle);

        assertEquals(1, world.getVehicles().size());
    }

    @Test
    void testWorldUpdate() {
        SimulationWorld world = new SimulationWorld();

        assertDoesNotThrow(() -> world.update(0.16));
    }

    @Test
    void testEmptyWorldUpdate() {
        SimulationWorld world = new SimulationWorld();

        world.update(0.16);

        assertTrue(world.getVehicles().isEmpty());
    }

    @Test
    void testMultipleVehicles() {
        SimulationWorld world = new SimulationWorld();

        world.addVehicle(VehicleFactory.create("car", createDummyPath()));
        world.addVehicle(VehicleFactory.create("bus", createDummyPath()));

        assertEquals(2, world.getVehicles().size());
    }

    @Test
    void testWorldConsistency() {
        SimulationWorld world = new SimulationWorld();

        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());
        world.addVehicle(vehicle);

        assertTrue(world.getVehicles().contains(vehicle));
    }
}