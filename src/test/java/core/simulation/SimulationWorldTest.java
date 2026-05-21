package core.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class SimulationWorldTest {

    @Test
    void shouldCreateWorld() {

        SimulationWorld world =
                new SimulationWorld();

        assertNotNull(world);
    }

    @Test
    void shouldUpdateWorld() {

        SimulationWorld world =
                new SimulationWorld();

        assertDoesNotThrow(() ->
                world.update(0.016));
    }
}