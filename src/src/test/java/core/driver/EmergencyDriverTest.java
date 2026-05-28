package core.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import core.road.VehiclePath;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.TrafficLight;
import core.vehicle.Ambulance;
import core.vehicle.Car;
import java.util.List;
import org.junit.jupiter.api.Test;
import util.Vector2D;

public class EmergencyDriverTest {

    @Test
    void testEmergencyDriverCreation() {
        EmergencyDriver driver = new EmergencyDriver();
        assertNotNull(driver);
    }

    @Test
    void testDriverStyleName() {
        EmergencyDriver driver = new EmergencyDriver();
        assertNotNull(driver.getStyleName());
    }

    @Test
    void inactiveSirenStopsAtRedLight() {
        VehiclePath path = new VehiclePath("path", List.of(
                new Vector2D(0, 0),
                new Vector2D(50, 0),
                new Vector2D(100, 0)), 1, "red-light", "A", "B");
        Ambulance ambulance = new Ambulance("amb-1", path, new EmergencyDriver());
        ambulance.setSirenActive(false);
        SimulationWorld world = new SimulationWorld();
        world.registerTrafficLight(new FixedTrafficLight("red-light", LightColor.RED));

        DrivingDecision decision = new EmergencyDriver().decide(ambulance, world);

        assertEquals(DrivingAction.STOP, decision.getAction());
    }

    @Test
    void activeSirenIgnoresRedLight() {
        VehiclePath path = new VehiclePath("path", List.of(
                new Vector2D(0, 0),
                new Vector2D(50, 0),
                new Vector2D(100, 0)), 1, "red-light", "A", "B");
        Ambulance ambulance = new Ambulance("amb-1", path, new EmergencyDriver());
        SimulationWorld world = new SimulationWorld();
        world.registerTrafficLight(new FixedTrafficLight("red-light", LightColor.RED));

        DrivingDecision decision = new EmergencyDriver().decide(ambulance, world);

        assertEquals(DrivingAction.EMERGENCY_PASS, decision.getAction());
    }

    @Test
    void activeSirenBrakesWhenObstacleStillBlocksLane() {
        VehiclePath ambulancePath = new VehiclePath("amb-path", List.of(
                new Vector2D(0, 0),
                new Vector2D(300, 0)), 1, null, "W", "E");
        VehiclePath carPath = new VehiclePath("car-path", List.of(
                new Vector2D(60, 0),
                new Vector2D(300, 0)), 1, null, "W", "E");
        Ambulance ambulance = new Ambulance("amb-1", ambulancePath, new EmergencyDriver());
        Car blockingCar = new Car("car-1", carPath, new NormalDriver());
        SimulationWorld world = new SimulationWorld();
        world.addVehicle(ambulance);
        world.addVehicle(blockingCar);

        DrivingDecision decision = new EmergencyDriver().decide(ambulance, world);

        assertEquals(DrivingAction.BRAKE, decision.getAction());
    }

    private static final class FixedTrafficLight extends TrafficLight {
        private FixedTrafficLight(String id, LightColor color) {
            this.id = id;
            this.currentColor = color;
        }

        @Override
        public boolean shouldShowCountdown() {
            return false;
        }
    }
}
