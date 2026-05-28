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
    //  Xe ưu tiên
    // ─────────────────────────────────────────────────────────────────

    /**
     * Xe hiện tại có phải nhường đường cho xe ưu tiên nào đó không?
     * Điều kiện: có {@link PriorityVehicle} trong bán kính sirên của nó,
     * và đang tiến gần xe hiện tại.
     */
    public boolean shouldYieldToPriorityVehicle(Vehicle self, SimulationWorld world) {
        if (self.isPriorityVehicle()) return false;   // xe ưu tiên không nhường nhau

        return world.getVehicles().stream()
                .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
                .map(v -> (PriorityVehicle) v)
                .anyMatch(pv -> {
                    double dist = pv.getPosition().distanceTo(self.getPosition());
                    return dist <= pv.getSirenRadius();
                });
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

        final double BOX_R      = 62.0;  // px — bên trong hộp giao lộ (ROAD_HALF=50, diagonal~71)
        final double APPROACH_R = 85.0;  // px — vùng tiếp cận trước vạch dừng

        util.Vector2D myPos = self.getEffectivePosition();
        util.Vector2D nearest = centers.stream()
                .min(java.util.Comparator.comparingDouble(c -> myPos.distanceTo(c)))
                .orElse(null);
        if (nearest == null) return ConflictLevel.NONE;

        double myDist = myPos.distanceTo(nearest);
        if (myDist > APPROACH_R) return ConflictLevel.NONE;

        // Xe đã vượt qua hộp giao lộ và đang ra ngoài (exiting) → không còn trong vùng conflict
        // isInIntersection=true nhưng myDist > BOX_R nghĩa là xe đã đi qua tâm, đang thoát ra
        boolean iAmInside   = myDist <= BOX_R;
        if (!iAmInside && self.isInIntersection()) return ConflictLevel.NONE;

        boolean iAmStraight = isGoingStraight(self.getPath().getEntryArm(), self.getPath().getExitArm());
        String  myEntry     = self.getPath().getEntryArm();

        ConflictLevel worst = ConflictLevel.NONE;

        for (core.vehicle.Vehicle other : world.getVehicles()) {
            if (other == self)      continue;
            if (other.isCrashed())  continue;
            if (other.isFinished()) continue;

            String theirEntry = other.getPath().getEntryArm();
            if (theirEntry.equals(myEntry)) continue; // cùng arm → cùng làn, không cắt nhau

            // Xe kia đang đèn đỏ/vàng và chưa vào hộp → đèn đã điều phối, bỏ qua
            LightColor theirLight = getApproachingLightColor(other, world);
            if (theirLight != LightColor.GREEN && !other.isInIntersection()) continue;

            double  theirDist    = other.getEffectivePosition().distanceTo(nearest);
            boolean theyInside   = theirDist <= BOX_R;

            // Xe kia đã vượt qua hộp và đang thoát ra → không còn là mối đe dọa
            if (!theyInside && other.isInIntersection()) continue;

            boolean theyStraight = isGoingStraight(theirEntry, other.getPath().getExitArm());

            // Hai xe đi thẳng ngược chiều (N↔S hoặc E↔W) — dùng làn riêng, không đâm nhau
            if (isOpposite(myEntry, theirEntry) && iAmStraight && theyStraight) continue;

            // ── Xe kia đang trong hộp giao lộ ──────────────────────────────────────
            if (theyInside) {
                if (iAmInside) {
                    // Cả hai trong hộp cùng lúc:
                    // Xe rẽ nhường xe thẳng; nếu cả hai đều rẽ → xe nào vào trước tiếp tục (YIELD)
                    if (!iAmStraight && theyStraight) {
                        // Tôi rẽ, họ thẳng → tôi phải STOP để tránh đâm
                        return ConflictLevel.STOP;
                    }
                    // Tôi thẳng, họ rẽ → tôi có quyền ưu tiên → chỉ YIELD nhẹ đề phòng
                    // Cả hai đều rẽ → YIELD nhẹ
                    worst = ConflictLevel.YIELD;
                } else {
                    // Họ đang trong hộp, tôi đang tiếp cận → tôi phải STOP chờ
                    return ConflictLevel.STOP;
                }
            } else {
                // Xe kia đang tiếp cận (chưa vào hộp)
                if (iAmInside) {
                    // Tôi đang trong hộp, họ chưa vào → tôi có quyền ưu tiên, đi tiếp bình thường
                    continue;
                } else {
                    // Cả hai đang tiếp cận — chỉ xe ĐANG RẼ mới nhường, xe thẳng có ưu tiên
                    if (!iAmStraight) {
                        // Tôi đang rẽ → nhường nếu xe kia (đi thẳng) gần hộp hơn hoặc xấp xỉ
                        if (theyStraight && theirDist <= myDist + 20) {
                            worst = ConflictLevel.STOP;
                        }
                        // Cả hai đều rẽ và khoảng cách xấp xỉ → YIELD nhẹ
                        if (!theyStraight && Math.abs(theirDist - myDist) < 10) {
                            if (worst == ConflictLevel.NONE) worst = ConflictLevel.YIELD;
                        }
                    }
                    // Tôi đi thẳng → không nhường ai khi cả hai đang tiếp cận
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
