package core.vehicle;

import core.driver.DriverBehavior;
import core.driver.DrivingDecision;
import core.model.Entity;
import core.model.Movable;
import core.road.Lane;
import core.road.Route;
import core.simulation.SimulationWorld;
import util.Vector2D;

import static core.driver.DrivingDecision.brake;

public abstract class Vehicle extends Entity implements Movable {
    protected Vector2D velocity;
    protected double maxSpeed;
    protected double acceleration;
    protected double length;
    protected double width;

    protected VehicleProfile profile;
    protected DriverBehavior driverBehavior;
    protected VehicleSoundProfile soundProfile;

    protected Lane currentLane;
    protected Route route;

    public Vehicle(
            String id,
            Vector2D position,
            DriverBehavior driverBehavior
    ) {
        this.id = id;
        this.position = position;
        this.driverBehavior = driverBehavior;
    }

    @Override
    public void update(double deltaTime, SimulationWorld world) {
        DrivingDecision decision = driverBehavior.decide(this, world);
        applyDecision(decision, deltaTime);
        move(deltaTime);
    }

    protected void applyDecision(DrivingDecision decision, double deltaTime) {
        switch (decision.getAction()) {
            case ACCELERATE -> accelerate(deltaTime);
            case BRAKE -> brake(deltaTime);
            case STOP -> stop();
            case CHANGE_LANE_LEFT -> changeLaneLeft();
            case CHANGE_LANE_RIGHT -> changeLaneRight();
            case YIELD -> yield();
        }
    }

    @Override
    public void move(double deltaTime) {
        position = position.add(velocity.multiply(deltaTime));
    }

    public abstract boolean isPriorityVehicle();
}