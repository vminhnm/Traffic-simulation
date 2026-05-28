package core.driver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void changesLaneToOvertakeSlowFrontVehicleWhenClear() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", path("ns1", 0, 0), new NormalDriver());
        Car slowFront = new Car("front", path("ns1", 60, 0), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(slowFront);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
    }

    @Test
    void yieldsBesideLaneForPriorityVehicleWhenClear() {
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", path("ns1", 0, 0), new NormalDriver());
        Ambulance ambulance = new Ambulance("amb", path("ns2", -70, 0), new EmergencyDriver());
        world.addVehicle(self);
        world.addVehicle(ambulance);

        DrivingDecision decision = new NormalDriver().decide(self, world);

        assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
    }

    private VehiclePath path(String id, double x, double y) {
        return new VehiclePath(id, List.of(
                new Vector2D(x, y),
                new Vector2D(x + 100, y)), 1, null, "A", "B");
    }
}
