package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.driver.NormalDriver;
import core.road.VehiclePath;
import util.Vector2D;

public class CarTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testCarCreation() {
        Car car = new Car("C1", createDummyPath(), new NormalDriver());
        assertNotNull(car);
    }

    @Test
    void testCarInheritance() {
        Car car = new Car("C1", createDummyPath(), new NormalDriver());
        assertTrue(car instanceof Vehicle);
    }

    @Test
    void testCarProfile() {
        Car car = new Car("C1", createDummyPath(), new NormalDriver());
        assertNotNull(car.getProfile());
    }

    @Test
    void testCarId() {
        Car car = new Car("C1", createDummyPath(), new NormalDriver());
        assertNotNull(car.getId());
    }
}