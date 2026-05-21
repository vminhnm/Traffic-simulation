package core.driver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;
import util.Vector2D;

class TrafficRuleEvaluatorTest {

    // Mock TrafficLight
    private static class TestTrafficLight extends TrafficLight {
        public TestTrafficLight(String id) { this.id = id; }
        @Override public boolean shouldShowCountdown() { return false; }
    }

    // Mock Vehicle
    private static class TestVehicle extends Vehicle {
        public TestVehicle(String id, VehiclePath path) {
            super(id, path, null);
        }
        @Override protected core.vehicle.VehicleProfile buildProfile() {
            return core.vehicle.VehicleProfile.builder("test").build();
        }
        @Override public boolean isPriorityVehicle() { return false; }
    }

    @Test
    void shouldDetectRedLight() {
        SimulationWorld world = new SimulationWorld();
        TestTrafficLight light = new TestTrafficLight("light_1");
        light.setColor(LightColor.RED);
        world.registerTrafficLight(light);

        VehiclePath path = new VehiclePath("p1", List.of(new Vector2D(0, 0), new Vector2D(100, 0)), 1, "light_1", "N", "S");
        TestVehicle vehicle = new TestVehicle("v1", path);
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();

        LightColor result = evaluator.getApproachingLightColor(vehicle, world);
        assertEquals(LightColor.RED, result);
    }

    @Test
    void shouldDetectGreenLight() {
        SimulationWorld world = new SimulationWorld();
        TestTrafficLight light = new TestTrafficLight("light_1");
        light.setColor(LightColor.GREEN);
        world.registerTrafficLight(light);

        VehiclePath path = new VehiclePath("p1", List.of(new Vector2D(0, 0), new Vector2D(100, 0)), 1, "light_1", "N", "S");
        TestVehicle vehicle = new TestVehicle("v1", path);
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();

        LightColor result = evaluator.getApproachingLightColor(vehicle, world);
        assertEquals(LightColor.GREEN, result);
    }
}