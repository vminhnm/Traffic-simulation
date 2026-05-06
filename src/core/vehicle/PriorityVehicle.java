package core.vehicle;

public abstract class PriorityVehicle extends Vehicle {
    protected boolean sirenOn;

    public PriorityVehicle(String id, Vector2D position, DriverBehavior behavior) {
        super(id, position, behavior);
        this.sirenOn = true;
    }

    @Override
    public boolean isPriorityVehicle() {
        return true;
    }

    public boolean isSirenOn() {
        return sirenOn;
    }
}