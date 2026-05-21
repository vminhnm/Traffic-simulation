package core.rule;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class TrafficRuleEvaluatorTest {

    @Test
    void testNullVehicle() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        assertThrows(Exception.class,
                () -> evaluator.mustStopAtRedLight(null, null));
    }
}