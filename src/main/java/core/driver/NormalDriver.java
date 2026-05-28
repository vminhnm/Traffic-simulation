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
 *   <li>Nếu có xe ưu tiên trong bán kính sirên → YIELD: dạt sang bên nếu cùng làn,
 *       hoặc dừng nếu khác làn.</li>
 *   <li>Nếu đèn đỏ / vàng phía trước và còn ở phía sau vạch → STOP.</li>
 *   <li>Nếu xung đột giao lộ → STOP hoặc BRAKE tùy mức độ.</li>
 *   <li>Nếu khoảng cách đến xe trước < safeDistance → BRAKE tỉ lệ.</li>
 *   <li>Ngược lại → ACCELERATE đến maxSpeed.</li>
 * </ol>
 */
public class NormalDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES = new TrafficRuleEvaluator();
    private static final double SAFETY_STOP_RANGE = 200.0;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── 1. Nhường xe ưu tiên (ưu tiên hơn đèn đỏ) ────────────────
        boolean shouldYield = RULES.shouldYieldToPriorityVehicle(vehicle, world);
        if (shouldYield) {
            var priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();

                // If inside intersection, only yield if we are on a collision trajectory
                // with the priority vehicle — don't yield if we've already cleared its path
                if (vehicle.isInIntersection() && !isCollisionTrajectory(vehicle, pv)) {
                    shouldYield = false;
                }

                if (shouldYield) {
                    boolean sameEntry = vehicle.getPath().getEntryArm()
                            .equals(pv.getPath().getEntryArm());
                    if (sameEntry) {
                        // Same road arm → try to shift sideways to clear the path.
                        DrivingDecision sideShift = sideShiftAwayFromPriority(
                                vehicle, world, pv, yieldingSideSpeed(vehicle));
                        if (sideShift != null) return sideShift;

                        // Cannot shift sideways (car is dead-centre in ambulance's path).
                        // Drive forward to clear the ambulance — overrides red light,
                        // because yielding to an emergency vehicle takes priority.
                        double gap = RULES.gapToFrontVehicle(vehicle, world);
                        double safeDistance = safeDistance(vehicle);
                        if (gap < 0 || gap >= safeDistance) {
                            return DrivingDecision.accelerate(vehicle.getMaxSpeed());
                        }
                        if (gap >= vehicle.getLength() * 0.8) {
                            double ratio = Math.max(0, gap / safeDistance);
                            return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.6);
                        }
                        return DrivingDecision.stop();
                    }

                    double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                    if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                        return DrivingDecision.stop();
                    }
                }
            }
        }

        // ── Return to lane centre if no longer yielding ──────────────────
        if (vehicle.getLateralOffset() != 0
                && !RULES.shouldYieldToPriorityVehicle(vehicle, world)) {

            if (!isAmbulanceClear(vehicle, world)) {
                // Ambulance still too close — stay stopped in displaced position
                return DrivingDecision.stop();
            }

            if (RULES.canShiftToOffset(vehicle, world, 0.0)) {
                return DrivingDecision.mergeBack();
            } else {
                return DrivingDecision.yield();
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
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.55);
            }
        }

        // ── 3. Giữ khoảng cách xe trước ────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = safeDistance(vehicle);

        if (gap >= 0 && gap < safeDistance) {
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

    /**
     * Kiểm tra xem xe có đang trên quỹ đạo va chạm với xe ưu tiên không.
     */
    private boolean isCollisionTrajectory(Vehicle vehicle, PriorityVehicle pv) {
        Vector2D selfDir = RULES.movementDirection(vehicle);
        Vector2D pvDir   = RULES.movementDirection(pv);
        Vector2D toPv    = pv.getPosition().subtract(vehicle.getPosition());
        double forwardToPv = selfDir.dot(toPv);
        return forwardToPv > 0
                && forwardToPv < vehicle.getLength() * 4.0
                && Math.abs(pvDir.dot(toPv.normalize())) < 0.7;
    }

    /**
     * Tìm hướng dạt sang bên tốt nhất để nhường đường cho xe ưu tiên,
     * chọn phía nào tạo ra khoảng cách ngang lớn nhất với xe ưu tiên.
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

    /**
     * Xác định offset ưu tiên (trái hoặc phải) dựa trên path id.
     */
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
        return Math.max(18.0, vehicle.getMaxSpeed() * 0.28);
    }

    private double safeDistance(Vehicle vehicle) {
        double speed = vehicle.getSpeed();
        double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
        return vehicle.getLength() * 2.0 + 20 + brakingDistance;
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
    public String getStyleName() { return "Normal"; }
}