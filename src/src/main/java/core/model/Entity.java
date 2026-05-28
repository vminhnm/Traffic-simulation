package core.model;

import core.simulation.SimulationWorld;
import util.Vector2D;

public abstract class Entity {
    protected String id;
    protected Vector2D position;
    protected double rotation;

    public abstract void update(double deltaTime, SimulationWorld world);
}
