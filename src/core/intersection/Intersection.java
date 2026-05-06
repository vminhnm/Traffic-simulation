package core.intersection;

import core.road.Road;
import core.trafficlight.TrafficLight;
import util.Vector2D;

import java.util.List;

public abstract class Intersection {
    protected String id;
    protected Vector2D center;
    protected List<Road> connectedRoads;
    protected List<TrafficLight> trafficLights;

    public abstract IntersectionType getType();

    public List<Road> getConnectedRoads() {
        return connectedRoads;
    }

    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }
}
