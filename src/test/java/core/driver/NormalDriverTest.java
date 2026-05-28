package core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.road.VehiclePath;
import core.simulation.SimulationWorld;
import core.vehicle.Ambulance;
import core.vehicle.Car;
import java.util.List;
import org.junit.jupiter.api.Test;
import util.Vector2D;

public class NormalDriverTest {

    @Test
    void testNormalDriverCreation() {
        NormalDriver driver = new NormalDriver();
        assertNotNull(driver);
    }

    @Test
    void testDriverStyleName() {
        NormalDriver driver = new NormalDriver();
        assertNotNull(driver.getStyleName());
    }

    @Test
    void overtakesSlowFrontVehicleWhenSideCorridorIsClear() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", eastbound("self", 0, 0), new NormalDriver());
        Car slowFront = new Car("front", eastbound("front", 90, 0), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(slowFront);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertTrue(isLaneChange(decision), "Driver should overtake a slow front vehicle when the side corridor is clear.");
    }

    @Test
    void yieldsToPriorityByMovingAsideWhenSideCorridorIsClear() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", eastbound("self", 80, 0), new NormalDriver());
        Ambulance ambulance = new Ambulance("amb", eastbound("amb", 20, 0), new EmergencyDriver());
        world.addVehicle(self);
        world.addVehicle(ambulance);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertTrue(isLaneChange(decision), "Driver should move beside the lane for a priority vehicle behind it.");
    }

    @Test
    void doesNotMoveAdjacentCarIntoPriorityLane() {
        SimulationWorld world = new SimulationWorld();
        Car adjacent = new Car("adjacent", eastbound("adjacent", 80, 24), new NormalDriver());
        Ambulance ambulance = new Ambulance("amb", eastbound("amb", 20, 0), new EmergencyDriver());
        world.addVehicle(adjacent);
        world.addVehicle(ambulance);

        DrivingDecision decision = new NormalDriver().decide(adjacent, world);

        assertEquals(DrivingAction.ACCELERATE, decision.getAction());
    }

    @Test
    void prefersInnerSideForOuterIntersectionLane() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", eastbound("ns1", 80, 0), new NormalDriver());
        Ambulance ambulance = new Ambulance("amb", eastbound("amb", 20, 0), new EmergencyDriver());
        world.addVehicle(self);
        world.addVehicle(ambulance);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
    }

    @Test
    void stopsForPriorityWhenBothSideCorridorsAreBlocked() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", eastbound("self", 80, 0), new NormalDriver());
        Ambulance ambulance = new Ambulance("amb", eastbound("amb", 20, 0), new EmergencyDriver());
        Car leftBlocker = new Car("left", eastbound("left", 85, -VehicleSide.OFFSET), new NormalDriver());
        Car rightBlocker = new Car("right", eastbound("right", 85, VehicleSide.OFFSET), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(ambulance);
        world.addVehicle(leftBlocker);
        world.addVehicle(rightBlocker);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertEquals(DrivingAction.STOP, decision.getAction());
    }

    private static boolean isLaneChange(DrivingDecision decision) {
        return decision.getAction() == DrivingAction.CHANGE_LANE_LEFT
                || decision.getAction() == DrivingAction.CHANGE_LANE_RIGHT;
    }

    private static VehiclePath eastbound(String id, double startX, double y) {
        return new VehiclePath(id, List.of(
                new Vector2D(startX, y),
                new Vector2D(startX + 300, y)), 1, null, "W", "E");
    }

    private static final class VehicleSide {
        private static final double OFFSET = core.vehicle.Vehicle.SIDE_LANE_OFFSET;
    }
}
