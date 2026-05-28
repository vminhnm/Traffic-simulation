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

                // Same lane -> move beside the lane if a side corridor is clear.
                if (myLaneType.equals(theirLaneType)) {
                    if (RULES.canYieldRight(vehicle, world)) {
                        return DrivingDecision.changeLaneRight(vehicle.getMaxSpeed() * 0.35);
                    }
                    if (RULES.canYieldLeft(vehicle, world)) {
                        return DrivingDecision.changeLaneLeft(vehicle.getMaxSpeed() * 0.35);
                    }
                    return DrivingDecision.stop();
                }

                // Different lane → stop if in range and not already moving out
                double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                    return DrivingDecision.stop();
                }
            }
        }

        // ── 2. Chỉ dừng ở đèn ĐỎ (bỏ qua vàng) ────────────────────
        LightColor color = RULES.getApproachingLightColor(vehicle, world);
        if (color == LightColor.RED && RULES.isNearStopLine(vehicle, world)) {
            return DrivingDecision.stop();
        }

        // ── 3. Vượt xe chậm nếu có khoảng trống bên cạnh làn ────────
        if (RULES.hasSlowFrontVehicle(vehicle, world)) {
            if (RULES.canOvertakeRight(vehicle, world)) {
                return DrivingDecision.changeLaneRight(vehicle.getMaxSpeed() * SPEED_FACTOR);
            }
        }

        // ── 4. Khoảng cách bám sát ──────────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = vehicle.getLength() * 1.5 * SAFE_DIST_FACTOR + 8;

        if (gap >= 0 && gap < safeDistance) {
            double ratio = Math.max(0, gap / safeDistance);
            return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.4);
        }

        // ── 5. Phóng nhanh hơn giới hạn ─────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Aggressive"; }
}
