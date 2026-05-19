package core.road;

import util.Vector2D;
import java.util.List;

/**
 * Đường đi của một phương tiện: chuỗi waypoint từ điểm vào đến điểm ra.
 * Chỉ là Data Object — không chứa logic xử lý.
 */
public final class VehiclePath {

    private final String id;
    private final List<Vector2D> waypoints;
    /** Chỉ số waypoint nơi xe dừng chờ đèn đỏ */
    private final int stopIndex;
    /** ID đèn giao thông kiểm soát điểm dừng này */
    private final String trafficLightId;
    /** Tên cánh vào (N / S / E / W / NE ...) */
    private final String entryArm;
    /** Tên cánh ra */
    private final String exitArm;

    public VehiclePath(String id, List<Vector2D> waypoints,
                       int stopIndex, String trafficLightId,
                       String entryArm, String exitArm) {
        this.id             = id;
        this.waypoints      = List.copyOf(waypoints);
        this.stopIndex      = stopIndex;
        this.trafficLightId = trafficLightId;
        this.entryArm       = entryArm;
        this.exitArm        = exitArm;
    }

    public String         getId()             { return id;             }
    public List<Vector2D> getWaypoints()      { return waypoints;      }
    public int            getStopIndex()      { return stopIndex;      }
    public String         getTrafficLightId() { return trafficLightId; }
    public String         getEntryArm()       { return entryArm;       }
    public String         getExitArm()        { return exitArm;        }

    public Vector2D getStopPosition()  { return waypoints.get(stopIndex); }
    public Vector2D getStartPosition() { return waypoints.get(0); }
    public Vector2D getEndPosition()   { return waypoints.get(waypoints.size() - 1); }

    @Override
    public String toString() {
        return "VehiclePath[" + entryArm + "→" + exitArm + "]";
    }
}
