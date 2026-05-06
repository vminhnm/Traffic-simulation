package core.simulation;

import core.controller.TrafficController;
import core.road.RoadNetwork;
import core.vehicle.Vehicle;

import java.util.List;

public class SimulationWorld {
    private final RoadNetwork roadNetwork;
    private final List<Vehicle> vehicles;
    private final List<TrafficController> controllers;
    private final CollisionSystem collisionSystem;
    private final StatisticsCollector statisticsCollector;

    public void update(double deltaTime) {
        for (TrafficController controller : controllers) {
            controller.update(deltaTime);
        }

        for (Vehicle vehicle : vehicles) {
            vehicle.update(deltaTime, this);
        }

        collisionSystem.resolve(vehicles);
        statisticsCollector.collect(this);
    }
}