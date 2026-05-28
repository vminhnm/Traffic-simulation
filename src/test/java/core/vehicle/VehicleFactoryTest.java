package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import util.Vector2D;

public class VehicleFactoryTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testCreateCar() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());

        assertNotNull(vehicle);
        assertTrue(vehicle instanceof Car);
    }

    @Test
    void testCreateBus() {
        Vehicle vehicle = VehicleFactory.create("bus", createDummyPath());

        assertNotNull(vehicle);
        assertTrue(vehicle instanceof Bus);
    }

    @Test
    void testCreateFireTruck() {
        Vehicle vehicle = VehicleFactory.create("firetruck", createDummyPath());

        assertNotNull(vehicle);
        assertTrue(vehicle instanceof FireTruck);
    }

    @Test
    void testUniqueIds() {
        Vehicle v1 = VehicleFactory.create("car", createDummyPath());
        Vehicle v2 = VehicleFactory.create("car", createDummyPath());

        assertNotEquals(v1.getId(), v2.getId());
    }

    @Test
    void testInvalidVehicleType() {
        assertThrows(IllegalArgumentException.class,
                () -> VehicleFactory.create("tank", null));
    }

    @Test
    void testNullVehicleType() {
        assertThrows(Exception.class,
                () -> VehicleFactory.create(null, null));
    }

    @Test
    void testVehicleProfileAssigned() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());

        assertNotNull(vehicle.getProfile());
    }

    @Test
    void testVehicleInheritance() {
        Vehicle vehicle = VehicleFactory.create("bus", createDummyPath());

        assertTrue(vehicle instanceof Vehicle);
    }

    @Test
    void testGeneratedVehicleState() {
        Vehicle vehicle = VehicleFactory.create("car", createDummyPath());

        assertNotNull(vehicle.getId());
        assertNotNull(vehicle.getProfile());
    }
}