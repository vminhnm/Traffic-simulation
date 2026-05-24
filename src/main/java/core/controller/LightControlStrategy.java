package core.controller;

import core.intersection.Intersection;
import core.trafficlight.TrafficLight;

public interface LightControlStrategy {
    void update(Intersection intersection, double deltaTime);
    void handleManualSwitch(TrafficLight light);
}
