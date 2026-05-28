package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;

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

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── 1. Nhường xe ưu tiên: chỉ phanh/dừng tại chỗ, không chuyển làn ────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            var priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();
                double dist = pv.getPosition().distanceTo(vehicle.getPosition());

                // Càng gần xe ưu tiên càng phanh gấp hơn
                if (dist < vehicle.getLength() * 2.5) {
                    return DrivingDecision.stop();
                }
                // Giảm tốc mạnh để xe ưu tiên vượt qua
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.1);
            }
        }

        // ── 2. Kiểm tra đèn ─────────────────────────────────────────
        if (RULES.mustStopAtRedLight(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 2b. Kiểm tra xung đột trong giao lộ (xe rẽ chéo) ────────
        {
            var conflict = RULES.getIntersectionConflictLevel(vehicle, world);
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.STOP) {
                return DrivingDecision.stop();
            }
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.YIELD) {
                // Đang trong hộp, có xe khác cũng trong hộp nhưng mình có ưu tiên → chạy chậm vừa
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.55);
            }
        }

        // ── 3. Giữ khoảng cách xe trước — hoặc vượt nếu đủ điều kiện ─
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        
        double speed = vehicle.getSpeed();
        double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
        double safeDistance = vehicle.getLength() * 2.0 + 20 + brakingDistance;

        if (gap >= 0 && gap < safeDistance) {
            // Thử vượt nếu đủ điều kiện
            if (RULES.canOvertake(vehicle, world)) {
                return DrivingDecision.changeLaneLeft(vehicle.getMaxSpeed() * 0.9);
            }

            // Giảm tốc tỉ lệ: càng gần càng chậm
            if (gap < vehicle.getLength() * 0.8) {
                return DrivingDecision.stop();
            }
            
            double ratio      = Math.max(0, gap / safeDistance);
            double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.6;
            return DrivingDecision.brake(targetSpeed);
        }

        // Nếu đang ở làn vượt (lateralOffset âm) nhưng không còn xe chậm trước → về tâm
        if (vehicle.getLateralOffset() < -15 && gap < 0) {
            return DrivingDecision.accelerate(vehicle.getMaxSpeed());
        }

        // ── 4. Chạy bình thường ─────────────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed());
    }

    @Override
    public String getStyleName() { return "Normal"; }
}
