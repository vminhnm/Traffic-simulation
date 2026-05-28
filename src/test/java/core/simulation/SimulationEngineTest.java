package core.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class SimulationEngineTest {

    @Test
    void testEngineCreation() {
        SimulationEngine engine = new SimulationEngine(new SimulationWorld());
        assertNotNull(engine);
    }

    @Test
    void testEngineStart() {
        SimulationEngine engine = new SimulationEngine(new SimulationWorld());

        assertDoesNotThrow(engine::start);
    }

    @Test
    void testEngineUpdate() {
        SimulationEngine engine = new SimulationEngine(new SimulationWorld());

        assertDoesNotThrow(() -> engine.update(0.16));
    }
}