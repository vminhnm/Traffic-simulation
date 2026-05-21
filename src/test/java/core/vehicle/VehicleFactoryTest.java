package core.vehicle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import util.Vector2D;

class VehicleFactoryTest {

    private VehiclePath createMockPath() {
        return new VehiclePath("mock_path", List.of(Vector2D.ZERO, new Vector2D(100, 0)), 0, "light_1", "N", "S");
    }

    @Test
    void shouldCreateCar() {
        Vehicle vehicle = VehicleFactory.create("car", createMockPath());

        assertTrue(vehicle instanceof Car);
    }

    @Test
    void shouldCreateBus() {
        Vehicle vehicle = VehicleFactory.create("bus", createMockPath());

        assertTrue(vehicle instanceof Bus);
    }

    @Test
    void shouldCreateFireTruck() {
        Vehicle vehicle = VehicleFactory.create("firetruck", createMockPath());

        assertTrue(vehicle instanceof FireTruck);
    }

    @Test
    void shouldGenerateUniqueIds() {
        Vehicle a = VehicleFactory.create("car", createMockPath());
        Vehicle b = VehicleFactory.create("car", createMockPath());

        assertNotEquals(a.getId(), b.getId());
    }
}