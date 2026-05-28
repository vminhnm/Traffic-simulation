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
 *   <li>Vẫn nhường xe ưu tiên bằng cách dạt sang bên (không liều mạng).</li>
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

        // ── 1. Vẫn phải nhường xe ưu tiên ──────────────────────────
        if (RULES.shouldYieldToPriorityVehicle(vehicle, world)) {
            Optional<PriorityVehicle> priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();

                boolean sameEntry = vehicle.getPath().getEntryArm()
                        .equals(pv.getPath().getEntryArm());
                if (sameEntry) {
                    // Same road arm → dạt sang bên để nhường đường
                    DrivingDecision sideShift = sideShiftAwayFromPriority(
                            vehicle, world, pv, yieldingSideSpeed(vehicle));
                    return sideShift != null ? sideShift : DrivingDecision.stop();
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

        // ── 2b. Kiểm tra xung đột giao lộ — hung hăng vẫn tránh đâm ─
        {
            var conflict = RULES.getIntersectionConflictLevel(vehicle, world);
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.STOP) {
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.05);
            }
            if (conflict == core.rule.TrafficRuleEvaluator.ConflictLevel.YIELD) {
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.65);
            }
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

    /**
     * Tìm hướng dạt sang bên tốt nhất để nhường đường cho xe ưu tiên.
     */
    private DrivingDecision sideShiftAwayFromPriority(
            Vehicle vehicle, SimulationWorld world, PriorityVehicle priorityVehicle, double targetSpeed) {
        double currentDistance = RULES.lateralDistanceFromMovementLine(
                priorityVehicle, vehicle.getEffectivePosition());
        DrivingDecision bestDecision = null;
        double bestDistance = currentDistance;

        double preferredOffset = preferredSideOffset(vehicle);
        for (double targetOffset : new double[]{preferredOffset, -preferredOffset}) {
            if (!RULES.canShiftToOffset(vehicle, world, targetOffset)) continue;

            double candidateDistance = RULES.lateralDistanceFromMovementLine(
                    priorityVehicle, RULES.effectivePositionAtOffset(vehicle, targetOffset));
            if (candidateDistance > bestDistance) {
                bestDistance = candidateDistance;
                bestDecision = sideDecisionForOffset(targetOffset, targetSpeed);
            }
        }

        return bestDecision;
    }

    private double preferredSideOffset(Vehicle vehicle) {
        String pathId = vehicle.getPath().getId();
        if (!pathId.startsWith("grid-") && !pathId.startsWith("3w-")) {
            if (pathId.endsWith("1")) return 50.0;
            if (pathId.endsWith("0")) return -50.0;
        }
        return -50.0;
    }

    private DrivingDecision sideDecisionForOffset(double offset, double targetSpeed) {
        return offset < 0
                ? DrivingDecision.changeLaneLeft(targetSpeed)
                : DrivingDecision.changeLaneRight(targetSpeed);
    }

    private double yieldingSideSpeed(Vehicle vehicle) {
        return Math.max(20.0, vehicle.getMaxSpeed() * 0.32);
    }

    @Override
    public String getStyleName() { return "Aggressive"; }
}
