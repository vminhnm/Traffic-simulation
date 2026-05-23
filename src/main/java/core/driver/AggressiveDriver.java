package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.vehicle.Vehicle;

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

        // ── 1. Chỉ dừng ở đèn ĐỎ (bỏ qua vàng) ────────────────────
        LightColor color = RULES.getApproachingLightColor(vehicle, world);
        if (color == LightColor.RED && RULES.isNearStopLine(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 2. Vẫn phải nhường xe ưu tiên ──────────────────────────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            // Chỉ giảm tốc, không dừng hẳn như NormalDriver
            return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.3);
        }

        // ── 3. Khoảng cách bám sát ──────────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = vehicle.getLength() * 1.5 * SAFE_DIST_FACTOR + 8;

        if (gap >= 0 && gap < safeDistance) {
            // Càng gần xe trước (gap nhỏ) thì lực phanh phải càng MẠNH. Lỗi cũ đang tính ngược.
            double urgency = 1.0 - Math.max(0, gap / safeDistance);
            return DrivingDecision.brake(vehicle.getMaxSpeed() * urgency);
        }

        // ── 4. Phóng nhanh hơn giới hạn ─────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Aggressive"; }
}
