package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import java.util.Optional;

/**
 * <b>Lái xe hung hăng</b> — phóng nhanh, bỏ qua đèn vàng, bám sát xe trước.
 *
 * <h2>Khác biệt so với NormalDriver</h2>
 * <ul>
 *   <li>Chỉ dừng khi đèn ĐỎ và xe <em>chưa</em> vào vạch dừng —
 *       bỏ qua đèn vàng hoàn toàn.</li>
 *   <li>Khoảng cách an toàn bằng 0.6× so với NormalDriver.</li>
 *   <li>Tốc độ mục tiêu = {@code maxSpeed × 1.25} (tự vượt giới hạn 25%).</li>
 *   <li>Vẫn nhường xe ưu tiên — hung hăng không có nghĩa là liều mạng.</li>
 * </ul>
 */
public class AggressiveDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES = new TrafficRuleEvaluator();

    /** Hệ số tốc độ vượt giới hạn. */
    private static final double SPEED_FACTOR = 1.25;
    /** Hệ số khoảng cách an toàn (nhỏ hơn → bám sát hơn). */
    private static final double SAFE_DIST_FACTOR = 0.6;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── 1. Vẫn phải nhường xe ưu tiên: chỉ phanh/dừng, không chuyển làn ─────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            Optional<PriorityVehicle> priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();
                double dist = pv.getPosition().distanceTo(vehicle.getPosition());

                if (dist < vehicle.getLength() * 2.5) {
                    return DrivingDecision.stop();
                }
                // Hung hăng vẫn phải nhường — phanh mạnh
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.15);
            }
        }

        // ── 2. Chỉ dừng ở đèn ĐỎ (bỏ qua vàng) ────────────────────
        LightColor color = RULES.getApproachingLightColor(vehicle, world);
        if (color == LightColor.RED && RULES.isNearStopLine(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 2b. Kiểm tra xung đột giao lộ — hung hăng vẫn tránh đâm ─
        {
            var conflict = RULES.getIntersectionConflictLevel(vehicle, world);
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.STOP) {
                // Dense intersections need a full stop to avoid side-impact crashes.
                return DrivingDecision.stop();
            }
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.YIELD) {
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.65);
            }
        }

        // ── 3. Khoảng cách bám sát — hoặc vượt tích cực ────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = vehicle.getLength() * 1.5 * SAFE_DIST_FACTOR + 8;

        if (gap >= 0 && gap < safeDistance) {
            // Aggressive: thử vượt trước khi phanh
            if (RULES.canOvertake(vehicle, world)) {
                return DrivingDecision.changeLaneLeft(vehicle.getMaxSpeed() * SPEED_FACTOR);
            }
            double ratio = Math.max(0, gap / safeDistance);
            return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.4);
        }

        // ── 4. Phóng nhanh hơn giới hạn ─────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Aggressive"; }
}
