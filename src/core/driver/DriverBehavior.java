package core.driver;

import core.simulation.SimulationWorld;
import core.vehicle.Vehicle;

public interface DriverBehavior {
    DrivingDecision decide(Vehicle vehicle, SimulationWorld world);
}