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
                // Different lane → stop only if on a DIFFERENT axis (e.g. NS vs EW)
                if (isDifferentAxis(vehicle.getRotation(), pv.getRotation())) {
                    double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                    if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                        return DrivingDecision.stop();
                    }
                }
            }
        }

        // ── 2. Kiểm tra đèn ─────────────────────────────────────────
        if (RULES.mustStopAtRedLight(vehicle, world)) {
            return DrivingDecision.stop();
        }
        // ── 2b. Green-light queue release ───────────────────────────
        // If the light ahead is green and we are stopped (or very slow)
        // in a queue, allow a controlled creep-forward so vehicles
        // don't rear-end the car in front that is just starting to move.
        boolean lightIsGreen = RULES.isNearStopLine(vehicle, world)
                ? RULES.getApproachingLightColor(vehicle, world) == core.trafficlight.LightColor.GREEN
                : false;
        double queueCreepSpeed = vehicle.getMaxSpeed() * 0.25; // gentle follow speed

        // ── 3. Giữ khoảng cách xe trước ────────────────────────────
        double gap          = RULES.gapToFrontVehicle(vehicle, world);
        double speed = vehicle.getSpeed();
        double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
        double safeDistance = vehicle.getLength() * 2.0 + 20 + brakingDistance;

        if (gap >= 0 && gap < safeDistance) {
            // When the light is green and the front vehicle is accelerating
            // away from a stop, do not hard-stop — follow at a creep speed
            // so the queue flows without rear-ending the leader.
            lightIsGreen = RULES.getApproachingLightColor(vehicle, world)
                    == core.trafficlight.LightColor.GREEN;
            double frontSpeed = RULES.frontVehicleSpeed(vehicle, world);
            boolean frontIsMovingOrAccelerating = frontSpeed > 0 || lightIsGreen;

            if (gap < vehicle.getLength() * 0.8) {
                // Truly too close — only hard-stop if front is also stopped
                if (!frontIsMovingOrAccelerating) {
                    return DrivingDecision.stop();
                }
                // Front is moving; brake hard but do not stop
                return DrivingDecision.brake(Math.max(frontSpeed, vehicle.getMaxSpeed() * 0.05));
            }
            double ratio = Math.max(0, gap / safeDistance);
            double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.6;
            // When following a green-light queue release, ensure a minimum
            // creep speed so the queue doesn't stall behind a slow leader.
            if (lightIsGreen && vehicle.getSpeed() < vehicle.getMaxSpeed() * 0.1) {
                targetSpeed = Math.max(targetSpeed, vehicle.getMaxSpeed() * 0.15);
            }
            double decel = vehicle.getAcceleration() * 2.0;
            double physicalMax = Math.sqrt(2.0 * decel * Math.max(0, gap - vehicle.getLength() * 0.5));
            targetSpeed = Math.min(targetSpeed, physicalMax);
            return DrivingDecision.brake(targetSpeed);
}

        // ── 4. Chạy bình thường ─────────────────────────────────────
        return DrivingDecision.accelerate(vehicle.getMaxSpeed());
    }

    @Override
    public String getStyleName() { return "Normal"; }

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
