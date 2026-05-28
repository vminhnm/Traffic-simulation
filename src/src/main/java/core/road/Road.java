package core.road;

import java.util.List;

import util.Direction;
import util.Vector2D;

public class Road {
    private String id;
    private Vector2D start;
    private Vector2D end;
    private List<Lane> lanes;
    private double speedLimit;

    public Direction getDirection() {
        return null;
    }
}