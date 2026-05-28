package core.driver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
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

    private VehiclePath createPathAt(double x, double y, double dx, double dy) {
        return new VehiclePath("test_path", List.of(new Vector2D(x, y), new Vector2D(x + dx, y + dy)), 1, null, "Entry", "Exit");
    }

    private VehiclePath eastApproachPath(String id, double startX, double y, String exitArm) {
        return new VehiclePath(id, List.of(
                new Vector2D(startX, y),
                new Vector2D(startX - 100, y),
                new Vector2D(startX - 140, y - 40)), 1, null, "E", exitArm);
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

    @Test
    void collisionWorksForAllMainTravelDirections() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();

        Car east = new Car("east-1", createPathAt(0, 0, 100, 0), new NormalDriver());
        Car eastOverlap = new Car("east-2", createPathAt(10, 0, 100, 0), new NormalDriver());
        Car west = new Car("west-1", createPathAt(0, 30, -100, 0), new NormalDriver());
        Car westOverlap = new Car("west-2", createPathAt(-10, 30, -100, 0), new NormalDriver());
        Car south = new Car("south-1", createPathAt(60, 0, 0, 100), new NormalDriver());
        Car southOverlap = new Car("south-2", createPathAt(60, 10, 0, 100), new NormalDriver());
        Car north = new Car("north-1", createPathAt(90, 0, 0, -100), new NormalDriver());
        Car northOverlap = new Car("north-2", createPathAt(90, -10, 0, -100), new NormalDriver());
        Car diagonal = new Car("diag-1", createPathAt(130, 0, -100, 100), new NormalDriver());
        Car diagonalOverlap = new Car("diag-2", createPathAt(123, 7, -100, 100), new NormalDriver());

        assertTrue(evaluator.isColliding(east, eastOverlap), "Eastbound overlap must collide.");
        assertTrue(evaluator.isColliding(west, westOverlap), "Westbound overlap must collide.");
        assertTrue(evaluator.isColliding(south, southOverlap), "Southbound overlap must collide.");
        assertTrue(evaluator.isColliding(north, northOverlap), "Northbound overlap must collide.");
        assertTrue(evaluator.isColliding(diagonal, diagonalOverlap), "Diagonal overlap must collide.");
    }

    @Test
    void collisionIgnoresAdjacentLanesForAllMainAxes() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();

        Car east = new Car("east-1", createPathAt(0, 0, 100, 0), new NormalDriver());
        Car eastAdjacent = new Car("east-2", createPathAt(0, 24, 100, 0), new NormalDriver());
        Car west = new Car("west-1", createPathAt(0, 50, -100, 0), new NormalDriver());
        Car westAdjacent = new Car("west-2", createPathAt(0, 74, -100, 0), new NormalDriver());
        Car south = new Car("south-1", createPathAt(0, 100, 0, 100), new NormalDriver());
        Car southAdjacent = new Car("south-2", createPathAt(24, 100, 0, 100), new NormalDriver());
        Car north = new Car("north-1", createPathAt(50, 100, 0, -100), new NormalDriver());
        Car northAdjacent = new Car("north-2", createPathAt(74, 100, 0, -100), new NormalDriver());

        assertFalse(evaluator.isColliding(east, eastAdjacent), "Adjacent eastbound lanes must not collide.");
        assertFalse(evaluator.isColliding(west, westAdjacent), "Adjacent westbound lanes must not collide.");
        assertFalse(evaluator.isColliding(south, southAdjacent), "Adjacent southbound lanes must not collide.");
        assertFalse(evaluator.isColliding(north, northAdjacent), "Adjacent northbound lanes must not collide.");
    }

    @Test
    void eastToWestCollisionBoundaryIsStable() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        Car eastWest = new Car("east-west", createPathAt(100, 0, -100, 0), new NormalDriver());
        Car barelySeparated = new Car("east-west-separated", createPathAt(63, 0, -100, 0), new NormalDriver());
        Car barelyOverlapping = new Car("east-west-overlap", createPathAt(65, 0, -100, 0), new NormalDriver());

        assertFalse(evaluator.isColliding(eastWest, barelySeparated), "E→W cars separated past body length must not collide.");
        assertTrue(evaluator.isColliding(eastWest, barelyOverlapping), "E→W cars closer than body length must collide.");
    }

    @Test
    void eastToWestKeepsGapBehindEastApproachTurningVehicle() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car straight = new Car("east-west", eastApproachPath("ew", 200, 100, "W"), new NormalDriver());
        Car turning = new Car("east-north", eastApproachPath("en", 140, 100, "N"), new NormalDriver());
        world.addVehicle(straight);
        world.addVehicle(turning);

        double gap = evaluator.gapToFrontVehicle(straight, world);

        assertTrue(gap >= 0, "E→W must see a front vehicle on the same east approach even when route ids differ.");
    }

    @Test
    void eastToWestIgnoresVehicleInAdjacentOppositeLane() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car eastWest = new Car("east-west", eastApproachPath("ew", 200, 100, "W"), new NormalDriver());
        Car westEast = new Car("west-east", new VehiclePath("we", List.of(
                new Vector2D(140, 124),
                new Vector2D(240, 124)), 1, null, "W", "E"), new NormalDriver());
        world.addVehicle(eastWest);
        world.addVehicle(westEast);

        double gap = evaluator.gapToFrontVehicle(eastWest, world);

        assertEquals(-1.0, gap, 1e-9, "Opposite traffic in the neighboring lane must not be treated as a front vehicle.");
    }

    @Test
    void slowFrontVehicleCanBeOvertakenWhenRightSidePathIsClear() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", createPathAt(0, 0), new NormalDriver());
        Car slowFront = new Car("front", createPathAt(60, 0), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(slowFront);

        assertTrue(evaluator.hasSlowFrontVehicle(self, world));
        assertTrue(evaluator.canOvertakeRight(self, world));
    }

    @Test
    void overtakeIsBlockedWhenSidePathWouldCollide() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", createPathAt(0, 0), new NormalDriver());
        Car slowFront = new Car("front", createPathAt(60, 0), new NormalDriver());
        Car sideBlocker = new Car("blocker", createPathAt(0, 50), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(slowFront);
        world.addVehicle(sideBlocker);

        assertTrue(evaluator.hasSlowFrontVehicle(self, world));
        assertFalse(evaluator.canOvertakeRight(self, world));
    }

    @Test
    void overtakeCannotUseOutwardLeftSide() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", createPathAt(0, 0), new NormalDriver());
        Car slowFront = new Car("front", createPathAt(60, 0), new NormalDriver());
        world.addVehicle(self);
        world.addVehicle(slowFront);

        assertFalse(evaluator.canOvertakeLeft(self, world));
    }

    @Test
    void lateralManeuverIsBlockedWhenProjectedBodyLeavesRoadCorridor() {
        TrafficRuleEvaluator evaluator = new TrafficRuleEvaluator();
        SimulationWorld world = new SimulationWorld();
        Car self = new Car("self", createPathAt(0, 0), new NormalDriver());
        world.addVehicle(self);

        assertFalse(evaluator.isLateralPathClear(self, world, 80.0));
    }
}
