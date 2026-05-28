package core.rule;

import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.TrafficLight;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;
import core.driver.DriverBehavior;
import core.road.VehiclePath;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import java.util.Comparator;
import java.util.Optional;

/**
 * <b>Bộ đánh giá luật giao thông.</b>
 *
 * <p>Cung cấp các phép tính dùng chung cho mọi {@link DriverBehavior}:
 * kiểm tra đèn đỏ, khoảng cách xe trước, xe ưu tiên gần đó…
 * Tách hoàn toàn khỏi Vehicle và TrafficController để dễ test độc lập.</p>
 *
 * <p>Không có trạng thái (stateless) — an toàn khi chia sẻ giữa nhiều instance.</p>
 */
public final class TrafficRuleEvaluator {

    /** Khoảng cách (px) từ xe đến stop-line để bắt đầu kiểm tra đèn. */
    private static final double LIGHT_CHECK_DISTANCE = 80.0;
    /** Khoảng cách (px) để coi là "đã vào vạch dừng" — không còn quay lại được. */
    private static final double COMMITTED_DISTANCE   = 10.0;
    private static final double INTERSECTION_BOX_RADIUS = 62.0;
    private static final double INTERSECTION_APPROACH_RADIUS = 120.0;
    private static final double INTERSECTION_ROUTE_RADIUS = 92.0;
    private static final double ARRIVAL_CONFLICT_WINDOW_SECONDS = 2.4;
    private static final double ETA_TIE_EPSILON_SECONDS = 0.35;

    // ─────────────────────────────────────────────────────────────────
    //  Đèn giao thông
    // ─────────────────────────────────────────────────────────────────

    /**
     * Xe có đang tiến đến đèn đỏ / vàng và cần dừng không?
     */
    public boolean mustStopAtRedLight(Vehicle vehicle, SimulationWorld world) {
        LightColor color = getApproachingLightColor(vehicle, world);
        if (color == LightColor.GREEN) return false;

        double distToStop = distanceToStopLine(vehicle);
        // Nếu đã vượt qua vạch dừng → không dừng lại nữa
        if (distToStop < -COMMITTED_DISTANCE) return false;
        // Nếu còn xa thì chưa cần lo
        if (distToStop > LIGHT_CHECK_DISTANCE) return false;

        return distToStop >= 0;
    }

    /**
     * Xe đã vào vùng "committed" (đã bắt đầu vượt vạch) chưa?
     * Nếu rồi thì không dừng dù đèn đỏ.
     */
    public boolean isNearStopLine(Vehicle vehicle, SimulationWorld world) {
        double dist = distanceToStopLine(vehicle);
        return dist >= 0 && dist <= LIGHT_CHECK_DISTANCE;
    }

    /**
     * Màu đèn của cánh đường mà xe đang tiến vào.
     * Trả về {@code GREEN} nếu không tìm thấy đèn (cho xe đi).
     */
    public LightColor getApproachingLightColor(Vehicle vehicle, SimulationWorld world) {
        String lightId = vehicle.getPath().getTrafficLightId();
        if (lightId == null || lightId.isBlank()) return LightColor.GREEN;

        return world.findTrafficLight(lightId)
                .map(TrafficLight::getColor)
                .orElse(LightColor.GREEN);
    }

    /**
     * Khoảng cách (px) từ đầu xe đến stop-line.
     * Âm nghĩa là xe đã vượt qua vạch.
     */
    public double distanceToStopLine(Vehicle vehicle) {
        VehiclePath path     = vehicle.getPath();
        Vector2D    stopPos  = path.getStopPosition();
        int         stopIdx  = path.getStopIndex();
        int         curIdx   = vehicle.getWaypointIndex();

        // Nếu xe đang nhắm đến waypoint sau stop-index → đã qua vạch
        if (curIdx > stopIdx) return -COMMITTED_DISTANCE - 1;

        Vector2D vehiclePos = vehicle.getPosition();

        // Tính khoảng cách dọc theo path từ xe đến stop-line
        if (curIdx == stopIdx) {
            // Đang tiến thẳng đến stop waypoint
            return vehiclePos.distanceTo(stopPos) - vehicle.getLength() / 2.0;
        }

        // Cần cộng các đoạn đường còn lại trước khi đến stop
        double remaining = vehiclePos.distanceTo(path.getWaypoints().get(curIdx));
        for (int i = curIdx; i < stopIdx; i++) {
            remaining += path.getWaypoints().get(i)
                             .distanceTo(path.getWaypoints().get(i + 1));
        }
        return remaining - vehicle.getLength() / 2.0;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Xe phía trước
    // ─────────────────────────────────────────────────────────────────

    /**
     * Khoảng cách (px) từ đầu xe hiện tại đến đuôi xe gần nhất phía trước
     * trên cùng làn/hướng đi. Xe có thể khác route khi chuẩn bị rẽ, nên
     * không chỉ so path id.
     *
     * @return khoảng cách dương nếu có xe trước; -1 nếu đường trống.
     */
    public double gapToFrontVehicle(Vehicle self, SimulationWorld world) {
        Vector2D pos  = self.getEffectivePosition();

        return world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> isAheadInSameLane(self, v))
                .min(Comparator.comparingDouble(v -> v.getEffectivePosition().distanceTo(pos)))
                .map(front -> {
                    double centerDist = front.getEffectivePosition().distanceTo(pos);
                    return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
                })
                .orElse(-1.0);
    }

    /**
     * {@code other} có ở phía trước {@code self} trong cùng làn không?
     */
    private boolean isAheadInSameLane(Vehicle self, Vehicle other) {
        Vector2D dir = movementDirection(self);
        if (dir.length() < 1e-9) return false;

        Vector2D toOther = other.getEffectivePosition().subtract(self.getEffectivePosition());
        double forwardDistance = dir.dot(toOther);
        if (forwardDistance <= 0) return false;

        Vector2D otherDir = movementDirection(other);
        if (otherDir.length() >= 1e-9 && dir.dot(otherDir) < 0.5) return false;

        Vector2D lateral = toOther.subtract(dir.multiply(forwardDistance));
        double sameLaneThreshold = (self.getWidth() + other.getWidth()) / 2.0 + 4.0;
        return lateral.length() <= sameLaneThreshold;
    }

    private Vector2D movementDirection(Vehicle vehicle) {
        Vector2D dir = vehicle.getVelocity();
        if (dir.length() >= 1e-9) return dir.normalize();

        int nextIdx = vehicle.getWaypointIndex();
        if (nextIdx < vehicle.getPath().getWaypoints().size()) {
            return vehicle.getPath().getWaypoints().get(nextIdx)
                    .subtract(vehicle.getPosition())
                    .normalize();
        }
        return Vector2D.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Vượt xe (Overtake)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Xe có thể vượt không?
     * Điều kiện: (1) có xe phía trước chậm hơn ngưỡng, (2) đường bên trái trống,
     * (3) xe chưa đang lệch (lateralOffset gần 0), (4) không đang trong giao lộ.
     */
    public boolean canOvertake(Vehicle self, SimulationWorld world) {
        // Không vượt khi đang ở trong/gần giao lộ
        if (self.isInIntersection()) return false;
        if (distanceToStopLine(self) < 60) return false;

        // Chỉ vượt khi lateralOffset gần tâm làn
        if (Math.abs(self.getLateralOffset()) > 8) return false;

        double gap = gapToFrontVehicle(self, world);
        if (gap < 0) return false; // Không có xe trước → không cần vượt

        // Tìm xe phía trước
        Vector2D pos = self.getEffectivePosition();
        var frontOpt = world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> isAheadInSameLane(self, v))
                .min(Comparator.comparingDouble(v -> v.getEffectivePosition().distanceTo(pos)));
        if (frontOpt.isEmpty()) return false;

        Vehicle front = frontOpt.get();
        // Chỉ vượt nếu xe trước đang chậm hơn đáng kể
        double slowThreshold = self.getMaxSpeed() * 0.6;
        if (front.getSpeed() >= slowThreshold) return false;

        // Kiểm tra đường bên trái trống: tìm xe trong vùng bên trái
        return isSideClear(self, world, -1);
    }

    /**
     * Kiểm tra bên trái (side=-1) hoặc bên phải (side=+1) có trống không.
     * Trả về true nếu không có xe nào trong vùng nguy hiểm khi chuyển làn.
     */
    public boolean isSideClear(Vehicle self, SimulationWorld world, int side) {
        Vector2D dir   = movementDirection(self);
        if (dir.length() < 1e-9) return false;

        // Vector vuông góc sang bên (side=-1 là trái, +1 là phải)
        Vector2D right  = new Vector2D(-dir.y, dir.x); // vector quay phải 90°
        Vector2D lateral = right.multiply(side);        // side=-1 → trái

        Vector2D myPos  = self.getEffectivePosition();
        double   checkOffset = (self.getWidth() + 10) * 2.0;
        double   checkLength = self.getLength() * 3.5;

        for (Vehicle other : world.getVehicles()) {
            if (other == self) continue;
            if (other.isCrashed() || other.isFinished()) continue;

            Vector2D toOther = other.getEffectivePosition().subtract(myPos);
            double sideComp  = toOther.dot(lateral);
            double fwdComp   = toOther.dot(dir);

            // Xe kia phải ở về phía bên cần kiểm tra
            if (sideComp < 5 || sideComp > checkOffset) continue;
            // Trong khoảng dọc ±checkLength quanh xe mình
            if (Math.abs(fwdComp) > checkLength) continue;

            return false; // Có xe → không an toàn
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Xe ưu tiên
    // ─────────────────────────────────────────────────────────────────

    /**
     * Xe hiện tại có phải nhường đường cho xe ưu tiên nào đó không?
     * Điều kiện: xe ưu tiên đang bật sirên VÀ xe hiện tại đang chắn đường
     * (nằm trong cùng làn phía trước xe ưu tiên, trong bán kính sirên).
     */
    public boolean shouldYieldToPriorityVehicle(Vehicle self, SimulationWorld world) {
        if (self.isPriorityVehicle()) return false;   // xe ưu tiên không nhường nhau

        return world.getVehicles().stream()
                .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
                .map(v -> (PriorityVehicle) v)
                .anyMatch(pv -> {
                    double dist = pv.getPosition().distanceTo(self.getPosition());
                    if (dist > pv.getSirenRadius()) return false;
                    // Chỉ nhường khi xe thường đang chắn đường xe ưu tiên
                    return isBlockingPriorityVehicle(self, pv);
                });
    }

    /**
     * Kiểm tra xe {@code self} có đang chắn đường xe ưu tiên {@code pv} không.
     * Điều kiện: {@code self} nằm phía trước {@code pv} theo hướng di chuyển
     * của {@code pv}, và nằm trong phạm vi ngang của làn xe ưu tiên.
     */
    private boolean isBlockingPriorityVehicle(Vehicle self, PriorityVehicle pv) {
        Vector2D pvDir = movementDirection(pv);
        if (pvDir.length() < 1e-9) return false;

        Vector2D toSelf = self.getEffectivePosition().subtract(pv.getEffectivePosition());
        double forwardDist = pvDir.dot(toSelf);
        // Xe thường phải ở phía trước xe ưu tiên
        if (forwardDist <= 0) return false;

        // Kiểm tra lệch ngang — không được vượt quá nửa chiều rộng làn của mỗi xe
        Vector2D lateral = toSelf.subtract(pvDir.multiply(forwardDist));
        double laneThreshold = (pv.getWidth() + self.getWidth()) / 2.0 + 6.0;
        return lateral.length() <= laneThreshold;
    }

    /**
     * Tìm xe ưu tiên gần nhất đang hoạt động.
     */
    public Optional<PriorityVehicle> nearestActivePriorityVehicle(
            Vehicle self, SimulationWorld world) {
        return world.getVehicles().stream()
                .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
                .map(v -> (PriorityVehicle) v)
                .min(Comparator.comparingDouble(
                        pv -> pv.getPosition().distanceTo(self.getPosition())));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Va chạm (Collision)
    // ─────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────
    //  Xung đột tại giao lộ
    // ─────────────────────────────────────────────────────────────────

    /**
     * Phiên bản static của distanceToStopLine — dùng khi không có world.
     */
    public static double staticDistanceToStopLine(core.vehicle.Vehicle vehicle) {
        core.road.VehiclePath path = vehicle.getPath();
        int stopIdx = path.getStopIndex();
        int curIdx  = vehicle.getWaypointIndex();
        if (curIdx > stopIdx) return -1;
        util.Vector2D vehiclePos = vehicle.getPosition();
        util.Vector2D stopPos    = path.getStopPosition();
        if (curIdx == stopIdx) {
            return vehiclePos.distanceTo(stopPos) - vehicle.getLength() / 2.0;
        }
        double remaining = vehiclePos.distanceTo(path.getWaypoints().get(curIdx));
        for (int i = curIdx; i < stopIdx; i++) {
            remaining += path.getWaypoints().get(i).distanceTo(path.getWaypoints().get(i + 1));
        }
        return remaining - vehicle.getLength() / 2.0;
    }

    /**
     * Xe {@code self} có cần nhường đường vì xung đột tại giao lộ không?
     *
     * <p><b>Thuật toán — proximity-based, không dự đoán:</b>
     * <ol>
     *   <li>Tìm tâm giao lộ gần xe nhất (từ world).</li>
     *   <li>Nếu cả xe mình lẫn xe kia đều trong vùng nguy hiểm quanh tâm đó,
     *       VÀ entry arm khác nhau (khác chiều vào),
     *       VÀ khoảng cách thực tế đủ gần → trả về true.</li>
     * </ol>
     */
    /**
     * Mức độ xung đột tại giao lộ.
     * NONE    — không có xung đột, đi bình thường.
     * YIELD   — đang trong hộp, xe kia cũng trong hộp nhưng mình có ưu tiên (đi thẳng) → chậm lại một chút.
     * STOP    — phải dừng hẳn: (a) mình chưa vào và có xe đang trong hộp, hoặc
     *                           (b) mình đang rẽ và có xe đi thẳng cùng thời điểm.
     */
    public enum ConflictLevel { NONE, YIELD, STOP }

    /**
     * Trả về mức độ xung đột tại giao lộ gần nhất.
     *
     * <h3>Quy tắc ưu tiên</h3>
     * <ul>
     *   <li>Xe đi thẳng có quyền ưu tiên hơn xe rẽ.</li>
     *   <li>Xe đang trong hộp có quyền ưu tiên hơn xe đang tiếp cận.</li>
     *   <li>Nếu cả hai đều trong hộp: xe rẽ phải nhường xe thẳng.</li>
     * </ul>
     */
    public ConflictLevel getIntersectionConflictLevel(core.vehicle.Vehicle self, SimulationWorld world) {
        java.util.List<util.Vector2D> centers = world.getIntersectionCenters();
        if (centers.isEmpty()) return ConflictLevel.NONE;

        util.Vector2D myPos = self.getEffectivePosition();
        util.Vector2D nearest = centers.stream()
                .min(java.util.Comparator.comparingDouble(c -> myPos.distanceTo(c)))
                .orElse(null);
        if (nearest == null) return ConflictLevel.NONE;

        double myDist = myPos.distanceTo(nearest);
        if (myDist > INTERSECTION_APPROACH_RADIUS) return ConflictLevel.NONE;

        boolean iAmInside = myDist <= INTERSECTION_BOX_RADIUS;
        if (!iAmInside && isMovingAwayFromCenter(self, nearest)) return ConflictLevel.NONE;

        boolean iAmStraight = isGoingStraight(self.getPath().getEntryArm(), self.getPath().getExitArm());
        String myEntry = self.getPath().getEntryArm();

        ConflictLevel worst = ConflictLevel.NONE;

        for (core.vehicle.Vehicle other : world.getVehicles()) {
            if (other == self) continue;
            if (other.isCrashed() || other.isFinished()) continue;

            String theirEntry = other.getPath().getEntryArm();
            if (theirEntry.equals(myEntry)) continue;

            double theirDist = other.getEffectivePosition().distanceTo(nearest);
            boolean theyInside = theirDist <= INTERSECTION_BOX_RADIUS;
            if (theirDist > INTERSECTION_APPROACH_RADIUS && !theyInside) continue;
            if (!theyInside && isMovingAwayFromCenter(other, nearest)) continue;

            LightColor theirLight = getApproachingLightColor(other, world);
            if (!other.isPriorityVehicle() && theirLight != LightColor.GREEN && !theyInside) {
                continue;
            }

            boolean theyStraight = isGoingStraight(theirEntry, other.getPath().getExitArm());
            if (isOpposite(myEntry, theirEntry) && iAmStraight && theyStraight) continue;
            if (!pathsPhysicallyConflict(self, other, nearest)) continue;

            if (theyInside) {
                if (!iAmInside) return ConflictLevel.STOP;
                if (shouldStopForConflict(self, other, iAmStraight, theyStraight, nearest)) {
                    return ConflictLevel.STOP;
                }
                worst = ConflictLevel.YIELD;
                continue;
            }

            if (iAmInside) continue;

            if (arrivalsOverlap(self, other, nearest)
                    && shouldStopForConflict(self, other, iAmStraight, theyStraight, nearest)) {
                return ConflictLevel.STOP;
            }

            if (!iAmStraight) {
                if (theyStraight && theirDist <= myDist + 20) {
                    return ConflictLevel.STOP;
                }
                if (!theyStraight && Math.abs(theirDist - myDist) < 10) {
                    worst = ConflictLevel.YIELD;
                }
            }
        }
        return worst;
    }

    /** Wrapper backward-compat cho code cũ dùng boolean. */
    public boolean hasIntersectionConflict(core.vehicle.Vehicle self, SimulationWorld world) {
        return getIntersectionConflictLevel(self, world) != ConflictLevel.NONE;
    }

    /**
     * Hai entry arm có đối diện nhau không? (N↔S, E↔W)
     */
    private boolean isOpposite(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        char ca = a.charAt(0), cb = b.charAt(0);
        return (ca == 'N' && cb == 'S') || (ca == 'S' && cb == 'N')
            || (ca == 'E' && cb == 'W') || (ca == 'W' && cb == 'E');
    }

    /**
     * Xe có đi thẳng không? (entry và exit là hai arm đối diện)
     */
    private boolean isGoingStraight(String entry, String exit) {
        return isOpposite(entry, exit);
    }

    /** Helpers for predictive intersection conflict checks. */
    private boolean isMovingAwayFromCenter(Vehicle vehicle, Vector2D center) {
        Vector2D dir = movementDirection(vehicle);
        if (dir.length() < 1e-9) return false;

        Vector2D fromCenter = vehicle.getEffectivePosition().subtract(center);
        double dist = fromCenter.length();
        if (dist <= INTERSECTION_BOX_RADIUS || dist < 1e-9) return false;

        return dir.dot(fromCenter.normalize()) > 0.35;
    }

    private boolean arrivalsOverlap(Vehicle self, Vehicle other, Vector2D center) {
        return Math.abs(estimatedTimeToIntersection(self, center)
                - estimatedTimeToIntersection(other, center)) <= ARRIVAL_CONFLICT_WINDOW_SECONDS;
    }

    private boolean shouldStopForConflict(
            Vehicle self, Vehicle other, boolean selfStraight, boolean otherStraight, Vector2D center) {
        if (self.isPriorityVehicle() != other.isPriorityVehicle()) {
            return other.isPriorityVehicle();
        }

        if (selfStraight != otherStraight) {
            return !selfStraight && otherStraight;
        }

        double myEta = estimatedTimeToIntersection(self, center);
        double theirEta = estimatedTimeToIntersection(other, center);
        if (theirEta + ETA_TIE_EPSILON_SECONDS < myEta) return true;
        if (myEta + ETA_TIE_EPSILON_SECONDS < theirEta) return false;

        return self.getId().compareTo(other.getId()) > 0;
    }

    private double estimatedTimeToIntersection(Vehicle vehicle, Vector2D center) {
        double distanceToBox = Math.max(0.0,
                vehicle.getEffectivePosition().distanceTo(center) - INTERSECTION_BOX_RADIUS);
        double expectedSpeed = Math.max(vehicle.getSpeed(), vehicle.getMaxSpeed() * 0.35);
        return distanceToBox / Math.max(1.0, expectedSpeed);
    }

    private boolean pathsPhysicallyConflict(Vehicle first, Vehicle second, Vector2D center) {
        java.util.List<Vector2D> firstRoute = futureRoute(first);
        java.util.List<Vector2D> secondRoute = futureRoute(second);
        if (firstRoute.size() < 2 || secondRoute.size() < 2) return false;

        double clearance = (first.getWidth() + second.getWidth()) / 2.0 + 5.0;
        for (int i = 0; i < firstRoute.size() - 1; i++) {
            Vector2D a = firstRoute.get(i);
            Vector2D b = firstRoute.get(i + 1);
            if (!segmentNearCenter(a, b, center)) continue;

            for (int j = 0; j < secondRoute.size() - 1; j++) {
                Vector2D c = secondRoute.get(j);
                Vector2D d = secondRoute.get(j + 1);
                if (!segmentNearCenter(c, d, center)) continue;
                if (segmentDistance(a, b, c, d) <= clearance) return true;
            }
        }
        return false;
    }

    private java.util.List<Vector2D> futureRoute(Vehicle vehicle) {
        java.util.ArrayList<Vector2D> points = new java.util.ArrayList<>();
        points.add(vehicle.getPosition());

        java.util.List<Vector2D> waypoints = vehicle.getPath().getWaypoints();
        int start = Math.max(0, Math.min(vehicle.getWaypointIndex(), waypoints.size()));
        for (int i = start; i < waypoints.size(); i++) {
            Vector2D point = waypoints.get(i);
            if (points.get(points.size() - 1).distanceTo(point) > 1e-6) {
                points.add(point);
            }
        }
        return points;
    }

    private boolean segmentNearCenter(Vector2D a, Vector2D b, Vector2D center) {
        return pointToSegmentDistance(center, a, b) <= INTERSECTION_ROUTE_RADIUS;
    }

    private double segmentDistance(Vector2D a, Vector2D b, Vector2D c, Vector2D d) {
        if (segmentsIntersect(a, b, c, d)) return 0.0;
        return Math.min(
                Math.min(pointToSegmentDistance(a, c, d), pointToSegmentDistance(b, c, d)),
                Math.min(pointToSegmentDistance(c, a, b), pointToSegmentDistance(d, a, b)));
    }

    private double pointToSegmentDistance(Vector2D point, Vector2D a, Vector2D b) {
        Vector2D ab = b.subtract(a);
        double lenSq = ab.dot(ab);
        if (lenSq < 1e-9) return point.distanceTo(a);

        double t = point.subtract(a).dot(ab) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vector2D projection = a.add(ab.multiply(t));
        return point.distanceTo(projection);
    }

    private boolean segmentsIntersect(Vector2D a, Vector2D b, Vector2D c, Vector2D d) {
        double o1 = orientation(a, b, c);
        double o2 = orientation(a, b, d);
        double o3 = orientation(c, d, a);
        double o4 = orientation(c, d, b);

        if (o1 * o2 < 0 && o3 * o4 < 0) return true;
        return Math.abs(o1) < 1e-9 && onSegment(a, c, b)
                || Math.abs(o2) < 1e-9 && onSegment(a, d, b)
                || Math.abs(o3) < 1e-9 && onSegment(c, a, d)
                || Math.abs(o4) < 1e-9 && onSegment(c, b, d);
    }

    private double orientation(Vector2D a, Vector2D b, Vector2D c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private boolean onSegment(Vector2D a, Vector2D point, Vector2D b) {
        return point.x >= Math.min(a.x, b.x) - 1e-9
                && point.x <= Math.max(a.x, b.x) + 1e-9
                && point.y >= Math.min(a.y, b.y) - 1e-9
                && point.y <= Math.max(a.y, b.y) + 1e-9;
    }

    /**
     * Kiểm tra xem hai xe có đang va chạm vật lý không (có tính đến góc xoay).
     */
    public boolean isColliding(Vehicle v1, Vehicle v2) {
        Area area1 = getBoundingBox(v1);
        Area area2 = getBoundingBox(v2);
        
        area1.intersect(area2);
        return !area1.isEmpty();
    }

    private Area getBoundingBox(Vehicle v) {
        // Tạo hình chữ nhật với tâm ở (0, 0)
        Rectangle2D.Double rect = new Rectangle2D.Double(
                -v.getLength() / 2, -v.getWidth() / 2, v.getLength(), v.getWidth());
        // Tịnh tiến và xoay
        AffineTransform transform = new AffineTransform();
        Vector2D center = v.getEffectivePosition();
        transform.translate(center.x, center.y);
        transform.rotate(v.getRotation());
        return new Area(new Path2D.Double(rect, transform));
    }
}
