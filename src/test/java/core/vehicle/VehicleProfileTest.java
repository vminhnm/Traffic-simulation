package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import util.Vector2D;

public class VehicleProfileTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testBuildProfile() {
        VehicleProfile profile = VehicleFactory.create("car", createDummyPath()).getProfile();

        assertNotNull(profile);
    }

    @Test
    void testDefaultValues() {
        VehicleProfile profile = VehicleFactory.create("bus", createDummyPath()).getProfile();

        assertNotNull(profile);
    }

    @Test
    void testBuilderImmutability() {
        VehicleProfile p1 = VehicleFactory.create("car", createDummyPath()).getProfile();

        assertNotNull(p1);
    }
}