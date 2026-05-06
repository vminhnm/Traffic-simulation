package core.road;

import util.Direction;
import util.Vector2D;

import java.util.List;

public class Lane {
    private String id;
    private Road road;
    private List<Vector2D> pathPoints;
    private LaneType laneType;

    public Vector2D getPointAt(double distance) {}
    public Direction getDirectionAt(double distance) {}
}