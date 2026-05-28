package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;

/**
 * <b>Lái xe bình thường</b> — tuân thủ đèn giao thông, giữ khoảng cách an toàn.
 *
 * <h2>Thuật toán quyết định</h2>
 * <ol>
 *   <li>Nếu đèn đỏ / vàng phía trước và còn ở phía sau vạch → STOP.</li>
 *   <li>Nếu có xe ưu tiên trong bán kính sirên → YIELD (giảm về 0 và dạt sang).</li>
 *   <li>Nếu khoảng cách đến xe trước < 1.5 × thân xe → BRAKE tỉ lệ.</li>
 *   <li>Ngược lại → ACCELERATE đến maxSpeed.</li>
 * </ol>
 */
public class NormalDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES = new TrafficRuleEvaluator();
    private static final double SAFETY_STOP_RANGE = 200.0; // Range to stop other lanes

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── 1. Nhường xe ưu tiên (ưu tiên hơn đèn đỏ) ────────────────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            var priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();
                String myPathId = vehicle.getPath().getId();
                String theirPathId = pv.getPath().getId();

                // Extract lane type (first 2-3 chars before numbers)
                String myLaneType = myPathId.replaceAll("[0-9]", "");
                String theirLaneType = theirPathId.replaceAll("[0-9]", "");

                // Same lane → move out
                if (myLaneType.equals(theirLaneType)) {
                    return DrivingDecision.changeLaneLeft(0);
                }

                // Different lane → stop if in range and not already moving out
                double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                    return DrivingDecision.stop();
                }
            }
        }

        // ── 2. Kiểm tra đèn ─────────────────────────────────────────
        if (RULES.mustStopAtRedLight(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 3. Giữ khoảng cách xe trước ────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        
        double speed = vehicle.getSpeed();
        double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
        double safeDistance = vehicle.getLength() * 2.0 + 20 + brakingDistance;

        if (gap >= 0 && gap < safeDistance) {
            // Giảm tốc tỉ lệ: càng gần càng chậm
            if (gap < vehicle.getLength() * 0.8) {
                return DrivingDecision.stop();
            }
            
            double ratio      = Math.max(0, gap / safeDistance);
            double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.6;
            return DrivingDecision.brake(targetSpeed);
        }

        // ── 4. Chạy bình thường ─────────────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed());
    }

    @Override
    public String getStyleName() { return "Normal"; }
}
