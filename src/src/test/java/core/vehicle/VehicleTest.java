package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.driver.DriverBehavior;
import core.driver.DrivingDecision;
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

    @Test
    void laneChangeMovesLaterallyOverMultipleFramesAndStaysBounded() {
        Vehicle vehicle = VehicleFactory.create("car", longPath());
        vehicle.setDriverBehavior(driver(DrivingDecision.changeLaneRight(vehicle.getMaxSpeed() * 0.4)));

        vehicle.update(0.10, new SimulationWorld());
        double firstOffset = vehicle.getLateralOffset();
        vehicle.update(0.10, new SimulationWorld());
        double secondOffset = vehicle.getLateralOffset();

        assertTrue(firstOffset > 0);
        assertTrue(secondOffset > firstOffset);
        assertTrue(secondOffset <= Vehicle.SIDE_LANE_OFFSET);
    }

    @Test
    void mergeBackKeepsVehicleRollingWhileRecentering() {
        Vehicle vehicle = VehicleFactory.create("car", longPath());
        SimulationWorld world = new SimulationWorld();
        vehicle.setDriverBehavior(driver(DrivingDecision.changeLaneRight(vehicle.getMaxSpeed() * 0.5)));
        vehicle.update(0.50, world);
        double sideOffset = vehicle.getLateralOffset();

        vehicle.setDriverBehavior(driver(DrivingDecision.mergeBack(vehicle.getMaxSpeed() * 0.5)));
        vehicle.update(0.10, world);

        assertTrue(vehicle.getLateralOffset() < sideOffset);
        assertTrue(vehicle.getSpeed() > 0);
        assertTrue(!vehicle.isStopped());
    }

    private VehiclePath longPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(300, 0)), 1, null, "W", "E");
    }

    private DriverBehavior driver(DrivingDecision decision) {
        return new DriverBehavior() {
            @Override
            public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {
                return decision;
            }

            @Override
            public String getStyleName() {
                return "Test";
            }
        };
    }
}
