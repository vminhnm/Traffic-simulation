package core.rule;

import core.driver.DrivingDecision;
import core.vehicle.Vehicle;

public interface PriorityVehiclePolicy {
    boolean shouldYield(Vehicle normalVehicle, Vehicle priorityVehicle);
    DrivingDecision createYieldDecision(Vehicle normalVehicle);
}