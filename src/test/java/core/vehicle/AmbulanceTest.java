package core.vehicle;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import core.road.VehiclePath;
import util.Vector2D;

public class AmbulanceTest {

    private VehiclePath createDummyPath() {
        return new VehiclePath("path1", Arrays.asList(new Vector2D(0, 0), new Vector2D(10, 10)), 1, "lane1", "lane2", "light1");
    }

    @Test
    void testAmbulanceCreation() {
        Ambulance ambulance = new Ambulance("AMB1", createDummyPath());

        assertNotNull(ambulance);
    }

    @Test
    void testPriorityVehicle() {
        Ambulance ambulance = new Ambulance("AMB1", createDummyPath());

        assertTrue(ambulance.isPriorityVehicle());
    }

    @Test
    void testInheritance() {
        Ambulance ambulance = new Ambulance("AMB1", createDummyPath());

        assertTrue(ambulance instanceof Vehicle);
    }

    @Test
    void testVehicleHasProfile() {
        Ambulance ambulance = new Ambulance("AMB1", createDummyPath());

        assertNotNull(ambulance.getProfile());
    }

    @Test
    void testVehicleHasId() {
        Ambulance ambulance = new Ambulance("AMB1", createDummyPath());

        assertNotNull(ambulance.getId());
    }
}