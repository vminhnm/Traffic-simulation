package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;

/**
 * <b>Lái xe khẩn cấp</b> — dành riêng cho {@link core.vehicle.PriorityVehicle}.
 *
 * <h2>Đặc điểm</h2>
 * <ul>
 *   <li><b>Bỏ qua hoàn toàn đèn đỏ / vàng</b> — xe ưu tiên có quyền đi.</li>
 *   <li>Tốc độ = {@code maxSpeed × 1.4}.</li>
 *   <li>Khi có xe đang chắn thẳng đường ({@code gap < CRITICAL_GAP}),
 *       giảm tốc nhẹ thay vì phanh gấp — tránh đâm nhưng không dừng.</li>
 *   <li>Tín hiệu EMERGENCY_PASS thông báo cho SimulationEngine biết
 *       cần kích hoạt logic "xe thường tránh đường" cho vùng lân cận.</li>
 * </ul>
 */
public class EmergencyDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES        = new TrafficRuleEvaluator();
    private static final double               SPEED_FACTOR = 1.4;
    /** Khoảng cách tối thiểu (px) trước khi bắt đầu giảm tốc nhẹ. */
    private static final double               CRITICAL_GAP = 20.0;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── Siren off: behave like a normal vehicle ──────────────────────
        if (vehicle instanceof PriorityVehicle priorityVehicle
                && !priorityVehicle.isSirenActive()) {
            if (RULES.mustStopAtRedLight(vehicle, world)) {
                return DrivingDecision.stop();
            }

            double gap = RULES.gapToObstacleAhead(vehicle, world);
            if (gap >= 0) {
                double speed = vehicle.getSpeed();
                double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
                double safeGap = vehicle.getLength() * 0.5 + brakingDistance + 10;

                if (gap < safeGap) {
                    double ratio = Math.max(0, gap / safeGap);
                    double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.4;
                    return DrivingDecision.brake(targetSpeed);
                }

                double safeDistance = vehicle.getLength() * 1.5 + 15;
                if (gap < safeDistance) {
                    double ratio = Math.max(0, gap / safeDistance);
                    return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.6);
                }
            }

            return DrivingDecision.accelerate(vehicle.getMaxSpeed());
        }

        // ── Return to lane centre — identical logic to NormalDriver ──────
        if (vehicle.getLateralOffset() != 0
                && !RULES.shouldYieldToPriorityVehicle(vehicle, world)) {

            if (!isAmbulanceClear(vehicle, world)) {
                return DrivingDecision.stop();
            }

            if (isMergePathClear(vehicle, world)) {
                return DrivingDecision.mergeBack();
            } else {
                return DrivingDecision.yield();
            }
        }

        // ── Check physical gap ahead (normal vehicles may not have moved yet) ──
        double gap = RULES.gapToObstacleAhead(vehicle, world);

        if (gap >= 0) {
            double speed = vehicle.getSpeed();
            double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
            double safeGap = vehicle.getLength() * 0.5 + brakingDistance + 10;

            if (gap < safeGap) {
                double ratio = Math.max(0, gap / safeGap);
                double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.4;
                return DrivingDecision.brake(targetSpeed);
            }
        }

        return DrivingDecision.emergencyPass(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    private boolean isMergePathClear(Vehicle vehicle, SimulationWorld world) {
        double myOffset     = vehicle.getLateralOffset();
        double targetOffset = 0.0;

        double mergeMin = Math.min(myOffset, targetOffset);
        double mergeMax = Math.max(myOffset, targetOffset);

        return world.getVehicles().stream()
            .filter(other -> other != vehicle && !other.isPriorityVehicle())
            .noneMatch(other -> {
                boolean samePath = other.getPath().getEntryArm()
                    .equals(vehicle.getPath().getEntryArm());
                if (!samePath) return false;

                double otherOffset = other.getLateralOffset();
                boolean blocksLateral = otherOffset >= mergeMin && otherOffset < mergeMax;
                if (!blocksLateral) return false;

                Vector2D toOther = other.getPosition().subtract(vehicle.getPosition());
                Vector2D myDir = vehicle.getVelocity().length() > 1e-9
                    ? vehicle.getVelocity().normalize()
                    : vehicle.getPath().getWaypoints().get(vehicle.getWaypointIndex())
                        .subtract(vehicle.getPosition()).normalize();
                double forward = myDir.dot(toOther);
                return forward > -vehicle.getLength() && Math.abs(forward) < vehicle.getLength() * 3.0;
            });
    }

    private boolean isAmbulanceClear(Vehicle vehicle, SimulationWorld world) {
        return world.getVehicles().stream()
            .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
            .noneMatch(pv -> {
                boolean sameArm = ((PriorityVehicle) pv).getPath().getEntryArm()
                    .equals(vehicle.getPath().getEntryArm());
                if (!sameArm) return false;
                double dist = pv.getPosition().distanceTo(vehicle.getPosition());
                return dist < ((PriorityVehicle) pv).getSirenRadius() * 1.5;
            });
    }

    @Override
    public String getStyleName() { return "Emergency"; }
}