package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;
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
    private static final double SAFETY_STOP_RANGE = 200.0;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── 1. Vẫn phải nhường xe ưu tiên (ưu tiên hơn đèn đỏ) ──────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            Optional<PriorityVehicle> priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();
                String myPathId = vehicle.getPath().getId();
                String theirPathId = pv.getPath().getId();

                // Extract lane type (first 2-3 chars before numbers)
                String myLaneType = myPathId.replaceAll("[0-9]", "");
                String theirLaneType = theirPathId.replaceAll("[0-9]", "");

                // Same lane → move out
                if (myLaneType.equals(theirLaneType)) {
                    return DrivingDecision.changeLaneLeft(vehicle.getMaxSpeed() * 0.3);
                }

                // Different lane → stop if in range and not already moving out
                // Different lane → stop only if on a DIFFERENT axis (e.g. NS vs EW)
                if (isDifferentAxis(vehicle.getRotation(), pv.getRotation())) {
                double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                    return DrivingDecision.stop();
                }
                }
            }
        }

        // ── 2. Chỉ dừng ở đèn ĐỎ (bỏ qua vàng) ────────────────────
        LightColor color = RULES.getApproachingLightColor(vehicle, world);
        if (color == LightColor.RED && RULES.isNearStopLine(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 3. Khoảng cách bám sát ──────────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = vehicle.getLength() * 1.5 * SAFE_DIST_FACTOR + 8;

        if (gap >= 0 && gap < safeDistance) {
            double ratio = Math.max(0, gap / safeDistance);
            return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.4);
        }

        // ── 4. Phóng nhanh hơn giới hạn ─────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Aggressive"; }

    /**
     * Returns true if the two rotations are on different axes
     * (one is roughly N-S, the other roughly E-W).
     * Same axis (NS vs NS, or EW vs EW) returns false — no need to stop.
     */
    private static boolean isDifferentAxis(double rotA, double rotB) {
        // Normalise to 0–180° (collapse opposite directions onto same axis)
        double a = Math.toDegrees(rotA) % 180;
        double b = Math.toDegrees(rotB) % 180;
        if (a < 0) a += 180;
        if (b < 0) b += 180;
        // NS ≈ 90°, EW ≈ 0° or 180°. Axes differ when angular distance > 45°.
        double diff = Math.abs(a - b);
        if (diff > 90) diff = 180 - diff;
        return diff > 45;
    }
}
