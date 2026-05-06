package core.driver;

import core.road.Lane;

public class DrivingDecision {
    private DrivingAction action;
    private double targetSpeed;
    private Lane targetLane;

    public static DrivingDecision accelerate(double speed) {}
    public static DrivingDecision brake() {}
    public static DrivingDecision stop() {}
    public static DrivingDecision changeLane(Lane lane) {}
    public static DrivingDecision yield() {}
}