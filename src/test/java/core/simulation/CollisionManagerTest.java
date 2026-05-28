package core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.driver.NormalDriver;
import core.road.VehiclePath;
import core.vehicle.Ambulance;
import core.vehicle.Car;
import core.vehicle.FireTruck;
import core.vehicle.Vehicle;
import java.util.List;
import org.junit.jupiter.api.Test;
import util.Vector2D;

class CollisionManagerTest {

    private VehiclePath pathAt(double x, double y) {
        return new VehiclePath("test", List.of(
                new Vector2D(x, y),
                new Vector2D(x + 100, y)), 1, null, "A", "B");
    }

    private SimulationWorld worldWith(Vehicle... vehicles) {
        SimulationWorld world = new SimulationWorld();
        for (Vehicle vehicle : vehicles) {
            world.addVehicle(vehicle);
        }
        return world;
    }

    @Test
    void twoNormalVehiclesCrashTogether() {
        Car first = new Car("car-1", pathAt(0, 0), new NormalDriver());
        Car second = new Car("car-2", pathAt(5, 0), new NormalDriver());
        CollisionManager manager = new CollisionManager(0);

        List<CollisionEvent> events = manager.detectAndResolve(worldWith(first, second));

        assertEquals(1, events.size());
        assertEquals(CollisionEvent.Type.NORMAL_CRASH, events.get(0).getType());
        assertTrue(first.isCrashed());
        assertTrue(second.isCrashed());
    }

    @Test
    void priorityVehicleContactDoesNotCrashNormalVehicle() {
        Ambulance ambulance = new Ambulance("amb-1", pathAt(0, 0));
        Car car = new Car("car-1", pathAt(5, 0), new NormalDriver());
        CollisionManager manager = new CollisionManager(0);

        List<CollisionEvent> events = manager.detectAndResolve(worldWith(ambulance, car));

        assertEquals(1, events.size());
        assertEquals(CollisionEvent.Type.PRIORITY_PUSH, events.get(0).getType());
        assertFalse(ambulance.isCrashed());
        assertFalse(car.isCrashed());
    }

    @Test
    void priorityVehicleContactStartsCooldownToAvoidRepeatedPushEvents() {
        Ambulance ambulance = new Ambulance("amb-1", pathAt(0, 0));
        Car car = new Car("car-1", pathAt(5, 0), new NormalDriver());
        CollisionManager manager = new CollisionManager(2.0);
        SimulationWorld world = worldWith(ambulance, car);

        List<CollisionEvent> firstEvents = manager.detectAndResolve(world);
        List<CollisionEvent> secondEvents = manager.detectAndResolve(world);

        assertEquals(1, firstEvents.size());
        assertTrue(secondEvents.isEmpty());
        assertFalse(ambulance.isCrashed());
        assertFalse(car.isCrashed());
    }

    @Test
    void twoPriorityVehiclesYieldWithoutCrashing() {
        Ambulance ambulance = new Ambulance("amb-1", pathAt(0, 0));
        FireTruck fireTruck = new FireTruck("fire-1", pathAt(5, 0));
        CollisionManager manager = new CollisionManager(0);

        List<CollisionEvent> events = manager.detectAndResolve(worldWith(ambulance, fireTruck));

        assertEquals(1, events.size());
        assertEquals(CollisionEvent.Type.PRIORITY_YIELD, events.get(0).getType());
        assertFalse(ambulance.isCrashed());
        assertFalse(fireTruck.isCrashed());
    }

    @Test
    void spawnCooldownSuppressesCollision() {
        Car first = new Car("car-1", pathAt(0, 0), new NormalDriver());
        Car second = new Car("car-2", pathAt(5, 0), new NormalDriver());
        CollisionManager manager = new CollisionManager(2.0);
        manager.startSpawnCooldown(first);

        List<CollisionEvent> events = manager.detectAndResolve(worldWith(first, second));

        assertTrue(events.isEmpty());
        assertFalse(first.isCrashed());
        assertFalse(second.isCrashed());
    }

    @Test
    void collisionWorksAfterCooldownExpires() {
        Car first = new Car("car-1", pathAt(0, 0), new NormalDriver());
        Car second = new Car("car-2", pathAt(5, 0), new NormalDriver());
        CollisionManager manager = new CollisionManager(2.0);
        manager.startSpawnCooldown(first);
        manager.updateCooldowns(2.1);

        List<CollisionEvent> events = manager.detectAndResolve(worldWith(first, second));

        assertEquals(1, events.size());
        assertTrue(first.isCrashed());
        assertTrue(second.isCrashed());
    }
}
