package core.model;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import core.vehicle.VehicleFactory;
import util.Vector2D;

public class RenderableStateTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testRenderableStateCreation() {
        assertNotNull(VehicleFactory.create("car", createDummyPath()).toRenderableState());
    }
}