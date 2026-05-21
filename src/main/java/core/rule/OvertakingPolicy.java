package core.rule;

import core.road.Lane;
import core.simulation.SimulationWorld;
import core.vehicle.Vehicle;

public interface OvertakingPolicy {
    boolean canOvertake(Vehicle vehicle, SimulationWorld world);
    Lane findTargetLane(Vehicle vehicle, SimulationWorld world);
}