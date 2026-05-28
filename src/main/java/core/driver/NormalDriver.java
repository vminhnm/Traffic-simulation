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
            boolean shouldYield = RULES.shouldYieldToPriorityVehicle(vehicle, world);
            if (shouldYield) {
                var priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
                if (priorityVehicle.isPresent()) {
                    PriorityVehicle pv = priorityVehicle.get();

                    // If inside intersection, only yield if we are on a collision trajectory
                    // with the priority vehicle — don't yield if we've already cleared its path
                    if (vehicle.isInIntersection()) {
                        Vector2D selfDir = RULES.movementDirection(vehicle);
                        Vector2D pvDir   = RULES.movementDirection(pv);
                        Vector2D toPv    = pv.getPosition().subtract(vehicle.getPosition());
                        // Are we moving toward each other's paths?
                        double forwardToPv = selfDir.dot(toPv);
                        boolean onCollisionPath = forwardToPv > 0
                            && forwardToPv < vehicle.getLength() * 4.0
                            && Math.abs(pvDir.dot(toPv.normalize())) < 0.7; // crossing, not same axis
                        if (!onCollisionPath) {
                            // Already past or not on trajectory — don't block, let it continue
                            shouldYield = false;
                        }
                    }

                    if (shouldYield) {
                        String myEntry    = vehicle.getPath().getEntryArm();
                        String theirEntry = pv.getPath().getEntryArm();
                        if (myEntry.equals(theirEntry)) {
                            return DrivingDecision.changeLaneLeft(0);
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

            if (isMergePathClear(vehicle, world)) {
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
                // Đang trong hộp, có xe khác cũng trong hộp nhưng mình có ưu tiên → chạy chậm vừa
                return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.55);
            }
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

    private boolean isMergePathClear(Vehicle vehicle, SimulationWorld world) {
        double myOffset = vehicle.getLateralOffset();
        double targetOffset = 0.0; // merging toward centre

        // The "merge side" is the range of lateral offsets between myOffset and 0.
        // Any other vehicle whose lateral offset falls in that range (i.e. is between
        // us and the lane centre) is blocking our merge path and we must wait for it.
        double mergeMin = Math.min(myOffset, targetOffset);
        double mergeMax = Math.max(myOffset, targetOffset);

        return world.getVehicles().stream()
            .filter(other -> other != vehicle && !other.isPriorityVehicle())
            .noneMatch(other -> {
                // Must be on same road arm (same direction)
                boolean samePath = other.getPath().getEntryArm()
                    .equals(vehicle.getPath().getEntryArm());
                if (!samePath) return false;

                double otherOffset = other.getLateralOffset();

                // Is the other vehicle's offset inside our merge sweep range?
                boolean blocksLateral = otherOffset >= mergeMin && otherOffset < mergeMax;
                if (!blocksLateral) return false;

                // And close enough longitudinally to be a collision risk
                Vector2D toOther = other.getPosition().subtract(vehicle.getPosition());
                Vector2D myDir = vehicle.getVelocity().length() > 1e-9
                    ? vehicle.getVelocity().normalize()
                    : vehicle.getPath().getWaypoints().get(vehicle.getWaypointIndex())
                        .subtract(vehicle.getPosition()).normalize();
                double forward = myDir.dot(toOther);
                // block if the car is ahead OR alongside (not just strictly behind)
                return forward > -vehicle.getLength() && Math.abs(forward) < vehicle.getLength() * 3.0;
            });
    }

    private boolean isAmbulanceClear(Vehicle vehicle, SimulationWorld world) {
        return world.getVehicles().stream()
            .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
            .noneMatch(pv -> {
                // Only delay merge-back for ambulances on the same road arm
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
