package core.road;

import java.util.List;
import java.util.Optional;

import core.intersection.Intersection;
import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;

public class RoadNetwork {
    private List<Road> roads;
    private List<Intersection> intersections;

    public void addRoad(Road road) {}
    public void addIntersection(Intersection intersection) {}

    public List<Road> getConnectedRoads(Intersection intersection) {
        return java.util.Collections.emptyList();
    }

    public Optional<TrafficLight> findRelevantTrafficLight(Vehicle vehicle) {
        return Optional.empty();
    }
}
