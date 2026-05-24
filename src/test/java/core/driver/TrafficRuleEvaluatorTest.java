package core.driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import core.rule.TrafficRuleEvaluator;
import core.vehicle.Car;
import core.road.VehiclePath;
import util.Vector2D;

import java.util.List;

public class TrafficRuleEvaluatorTest {

    /*
    @Test
    void testNullVehicle() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        assertThrows(Exception.class,
                () -> evaluator.mustStopAtRedLight(null, null));
    }
    */

    // Helper để tạo một lộ trình ảo bắt đầu từ vị trí (x, y)
    private VehiclePath createPathAt(double x, double y) {
        return new VehiclePath("test_path", List.of(new Vector2D(x, y), new Vector2D(x + 100, y)), 1, null, "Entry", "Exit");
    }

    @Test
    void testVehiclesAreColliding() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        
        // Đặt hai xe ở vị trí rất gần nhau (tọa độ 50,50 và 55,55)
        Car car1 = new Car("car1", createPathAt(50, 50), new NormalDriver());
        Car car2 = new Car("car2", createPathAt(55, 55), new NormalDriver());

        assertTrue(evaluator.isColliding(car1, car2), "Hai xe ở vị trí chồng lấp phải được phát hiện va chạm.");
    }

    @Test
    void testVehiclesAreNotColliding() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        
        // Đặt hai xe ở vị trí cách xa nhau (0,0 và 200,200)
        Car car1 = new Car("car1", createPathAt(0, 0), new NormalDriver());
        Car car2 = new Car("car2", createPathAt(200, 200), new NormalDriver());

        assertFalse(evaluator.isColliding(car1, car2), "Hai xe ở xa nhau không được phát hiện va chạm.");
    }
}