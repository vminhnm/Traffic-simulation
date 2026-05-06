package core.rule;

import core.intersection.Intersection;
import core.simulation.SimulationWorld;
import core.vehicle.Vehicle;

public class TrafficRuleEvaluator {
    public boolean mustStopAtRedLight(Vehicle vehicle, SimulationWorld world) {}

    public boolean tooCloseToFrontVehicle(Vehicle vehicle, SimulationWorld world) {}

    public boolean shouldYieldToPriorityVehicle(Vehicle vehicle, SimulationWorld world) {}

    public boolean canOvertakeSafely(Vehicle vehicle, SimulationWorld world) {}

    public boolean canEnterIntersection(Vehicle vehicle, Intersection intersection) {}
}