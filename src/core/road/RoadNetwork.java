package core.road;

import core.intersection.Intersection;
import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;

import java.util.List;
import java.util.Optional;

public class RoadNetwork {
    private List<Road> roads;
    private List<Intersection> intersections;

    public void addRoad(Road road) {}
    public void addIntersection(Intersection intersection) {}

    public List<Road> getConnectedRoads(Intersection intersection) {}

    public Optional<TrafficLight> findRelevantTrafficLight(Vehicle vehicle) {}
}
