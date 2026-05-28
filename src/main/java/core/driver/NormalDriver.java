package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;

/**
 * Normal driver: obeys lights, keeps safe distance, yields to priority vehicles,
 * and may use the adjacent side of its lane for safe overtaking.
 */
public class NormalDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES = new TrafficRuleEvaluator();
    private static final double SAFETY_STOP_RANGE = 200.0;
    private static final double OVERTAKE_SPEED_FACTOR = 0.75;
    private static final double SLOW_FRONT_FACTOR = 0.65;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {
        boolean shouldYield = RULES.shouldYieldToPriorityVehicle(vehicle, world);
        if (shouldYield) {
            var priorityVehicle = RULES.nearestActivePriorityVehicle(vehicle, world);
            if (priorityVehicle.isPresent()) {
                PriorityVehicle pv = priorityVehicle.get();

                if (vehicle.isInIntersection() && !isCollisionTrajectory(vehicle, pv)) {
                    shouldYield = false;
                }

                if (shouldYield) {
                    boolean sameEntry = vehicle.getPath().getEntryArm()
                            .equals(pv.getPath().getEntryArm());
                    if (sameEntry) {
                        DrivingDecision sideShift = sideShiftAwayFromPriority(vehicle, world, pv, 0);
                        return sideShift != null ? sideShift : DrivingDecision.stop();
                    }

                    double dist = vehicle.getPosition().distanceTo(pv.getPosition());
                    if (dist < SAFETY_STOP_RANGE && Math.abs(vehicle.getLateralOffset()) < 30) {
                        return DrivingDecision.stop();
                    }
                }
            }
        }

        if (RULES.mustStopAtRedLight(vehicle, world)) {
            return DrivingDecision.stop();
        }

        var conflict = RULES.getIntersectionConflictLevel(vehicle, world);
        if (conflict == TrafficRuleEvaluator.ConflictLevel.STOP) {
            return DrivingDecision.stop();
        }
        if (conflict == TrafficRuleEvaluator.ConflictLevel.YIELD) {
            return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.55);
        }

        if (Math.abs(vehicle.getLateralOffset()) > 0.5) {
            double sideGap = RULES.gapToFrontVehicle(vehicle, world);
            double sideSafeDistance = safeDistance(vehicle);
            if (sideGap >= 0 && sideGap < sideSafeDistance) {
                return brakeForGap(vehicle, sideGap, sideSafeDistance);
            }
            if (RULES.canShiftToOffset(vehicle, world, 0.0)) {
                return DrivingDecision.mergeBack();
            }
            return DrivingDecision.accelerate(vehicle.getMaxSpeed() * OVERTAKE_SPEED_FACTOR);
        }

        double gap = RULES.gapToFrontVehicle(vehicle, world);
        double safeDistance = safeDistance(vehicle);

        if (gap >= 0 && gap < safeDistance) {
            var front = RULES.nearestFrontVehicle(vehicle, world);
            if (front.isPresent() && isSlowFront(vehicle, front.get(), gap, safeDistance)) {
                DrivingDecision sideShift = sideShiftDecision(
                        vehicle, world, vehicle.getMaxSpeed() * OVERTAKE_SPEED_FACTOR);
                if (sideShift != null) return sideShift;
            }

            return brakeForGap(vehicle, gap, safeDistance);
        }

        return DrivingDecision.accelerate(vehicle.getMaxSpeed());
    }

    private boolean isCollisionTrajectory(Vehicle vehicle, PriorityVehicle pv) {
        Vector2D selfDir = RULES.movementDirection(vehicle);
        Vector2D pvDir = RULES.movementDirection(pv);
        Vector2D toPv = pv.getPosition().subtract(vehicle.getPosition());
        double forwardToPv = selfDir.dot(toPv);
        return forwardToPv > 0
                && forwardToPv < vehicle.getLength() * 4.0
                && Math.abs(pvDir.dot(toPv.normalize())) < 0.7;
    }

    private DrivingDecision sideShiftDecision(Vehicle vehicle, SimulationWorld world, double targetSpeed) {
        double offset = vehicle.getLateralOffset();
        if (offset <= -Vehicle.SIDE_LANE_OFFSET * 0.5) {
            return DrivingDecision.changeLaneLeft(targetSpeed);
        }
        if (offset >= Vehicle.SIDE_LANE_OFFSET * 0.5) {
            return DrivingDecision.changeLaneRight(targetSpeed);
        }

        double preferredOffset = preferredSideOffset(vehicle);
        if (RULES.canShiftToOffset(vehicle, world, preferredOffset)) {
            return sideDecisionForOffset(preferredOffset, targetSpeed);
        }

        double fallbackOffset = -preferredOffset;
        if (RULES.canShiftToOffset(vehicle, world, fallbackOffset)) {
            return sideDecisionForOffset(fallbackOffset, targetSpeed);
        }
        return null;
    }

    private DrivingDecision sideShiftAwayFromPriority(
            Vehicle vehicle, SimulationWorld world, PriorityVehicle priorityVehicle, double targetSpeed) {
        double currentDistance = RULES.lateralDistanceFromMovementLine(
                priorityVehicle, vehicle.getEffectivePosition());
        DrivingDecision bestDecision = null;
        double bestDistance = currentDistance;

        double preferredOffset = preferredSideOffset(vehicle);
        for (double targetOffset : new double[] {preferredOffset, -preferredOffset}) {
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
            if (pathId.endsWith("1")) return Vehicle.SIDE_LANE_OFFSET;
            if (pathId.endsWith("0")) return -Vehicle.SIDE_LANE_OFFSET;
        }
        return -Vehicle.SIDE_LANE_OFFSET;
    }

    private DrivingDecision sideDecisionForOffset(double offset, double targetSpeed) {
        return offset < 0
                ? DrivingDecision.changeLaneLeft(targetSpeed)
                : DrivingDecision.changeLaneRight(targetSpeed);
    }

    private boolean isSlowFront(Vehicle vehicle, Vehicle front, double gap, double safeDistance) {
        return gap >= vehicle.getLength() * 0.8
                && gap < safeDistance
                && front.getSpeed() < vehicle.getMaxSpeed() * SLOW_FRONT_FACTOR;
    }

    private double safeDistance(Vehicle vehicle) {
        double speed = vehicle.getSpeed();
        double brakingDistance = (speed * speed) / (vehicle.getAcceleration() * 2);
        return vehicle.getLength() * 2.0 + 20 + brakingDistance;
    }

    private DrivingDecision brakeForGap(Vehicle vehicle, double gap, double safeDistance) {
        if (gap < vehicle.getLength() * 0.8) {
            return DrivingDecision.stop();
        }

        double ratio = Math.max(0, gap / safeDistance);
        double targetSpeed = vehicle.getMaxSpeed() * ratio * 0.6;
        return DrivingDecision.brake(targetSpeed);
    }

    @Override
    public String getStyleName() {
        return "Normal";
    }
}
