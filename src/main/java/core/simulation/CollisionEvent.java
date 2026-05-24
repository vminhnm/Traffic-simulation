package core.simulation;

import core.vehicle.Vehicle;
import util.Vector2D;

public final class CollisionEvent {

    public enum Type {
        NORMAL_CRASH,
        PRIORITY_PUSH,
        PRIORITY_YIELD
    }

    private final Vehicle first;
    private final Vehicle second;
    private final Type type;
    private final Vector2D position;

    public CollisionEvent(Vehicle first, Vehicle second, Type type, Vector2D position) {
        this.first = first;
        this.second = second;
        this.type = type;
        this.position = position;
    }

    public Vehicle getFirst() {
        return first;
    }

    public Vehicle getSecond() {
        return second;
    }

    public Type getType() {
        return type;
    }

    public Vector2D getPosition() {
        return position;
    }
}
