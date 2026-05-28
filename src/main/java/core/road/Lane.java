package core.road;

import java.util.List;

import util.Direction;
import util.Vector2D;

public class Lane {
    private String id;
    private Road road;
    private List<Vector2D> pathPoints;
    private LaneType laneType;

    public Vector2D getPointAt(double distance) {
        return null;
    }
    public Direction getDirectionAt(double distance) {
        return null;
    }
}