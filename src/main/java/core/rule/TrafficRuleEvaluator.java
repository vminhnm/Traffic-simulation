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
     * Tâm giao lộ ước tính từ path của một xe:
     * trung bình của các waypoint nằm trong đoạn giao lộ (sau stopIndex).
     */
    private static Vector2D intersectionCenter(core.vehicle.Vehicle v) {
        java.util.List<Vector2D> wps = v.getPath().getWaypoints();
        int stop = v.getPath().getStopIndex();
        // lấy waypoint ngay sau stop-line
        int mid = Math.min(stop + 1, wps.size() - 1);
        return wps.get(mid);
    }

    /**
     * Kiểm tra xe {@code self} có cần nhường đường vì xung đột giao lộ không.
     *
     * <p><b>Thuật toán mới — đơn giản, đáng tin cậy:</b>
     * <ol>
     *   <li>Tính "vùng giao lộ" = hình tròn bán kính {@code ZONE_R} quanh tâm giao lộ
     *       (ước tính từ waypoint ngay sau stop-line của xe mình).</li>
     *   <li>Với mỗi xe khác từ entry arm khác, kiểm tra:
     *       <ul>
     *         <li>Xe đó đang <em>ở trong</em> vùng giao lộ, HOẶC</li>
     *         <li>Xe đó đang tiến vào và sẽ đến vùng giao lộ trước/cùng lúc xe mình.</li>
     *       </ul>
     *   </li>
     *   <li>Nếu có nguy cơ chéo đường (exit arm khác nhau), brake/stop.</li>
     * </ol>
     */
    public boolean hasIntersectionConflict(core.vehicle.Vehicle self, SimulationWorld world) {
        // Bán kính vùng nguy hiểm tại giao lộ
        final double ZONE_R       = 90.0;  // px — vùng giao lộ
        final double LOOK_AHEAD   = 130.0; // px — tầm nhìn trước vạch dừng

        String myEntry = self.getPath().getEntryArm();
        String myExit  = self.getPath().getExitArm();

        // Tâm giao lộ từ góc nhìn của xe mình
        Vector2D myCenter = intersectionCenter(self);

        // Khoảng cách của xe mình đến tâm giao lộ
        double myDistToCenter = self.getEffectivePosition().distanceTo(myCenter);

        // Nếu xe mình đã ra khỏi vùng giao lộ → không cần kiểm tra nữa
        if (self.isInIntersection() && myDistToCenter > ZONE_R) return false;

        for (core.vehicle.Vehicle other : world.getVehicles()) {
            if (other == self)           continue;
            if (other.isCrashed())       continue;
            if (other.isFinished())      continue;

            String theirEntry = other.getPath().getEntryArm();
            String theirExit  = other.getPath().getExitArm();

            // Cùng entry arm → đang xếp hàng cùng chiều, không cần lo
            if (theirEntry.equals(myEntry)) continue;

            // Nếu cả hai exit ra cùng một arm VÀ đi thẳng (entry đối diện nhau) → không chéo
            // Ví dụ N→S và S→N: ngược chiều nhưng không cắt nhau
            if (isOpposite(myEntry, theirEntry) && myExit.equals(theirExit) == false
                    && isGoingStraight(theirEntry, theirExit) && isGoingStraight(myEntry, myExit)) {
                continue; // hai xe đi thẳng ngược chiều, không cắt nhau
            }

            // Vị trí xe kia
            Vector2D theirPos = other.getEffectivePosition();
            double theirDistToCenter = theirPos.distanceTo(myCenter);

            boolean theirInZone = theirDistToCenter <= ZONE_R;

            // Tính khoảng cách xe kia đến tâm giao lộ dọc theo path
            double theirPathDistToCenter = pathDistanceToPoint(other, myCenter);

            // Xe kia chưa gần giao lộ → bỏ qua
            if (!theirInZone && theirPathDistToCenter > LOOK_AHEAD * 2) continue;

            // Kiểm tra hai path có thực sự giao nhau không
            if (!pathsWillCross(myEntry, myExit, theirEntry, theirExit)) continue;

            // ── Xe kia đang trong vùng giao lộ → mình phải dừng ──
            if (theirInZone) {
                // Nếu mình cũng đã trong giao lộ thì brake nhẹ (không dừng hẳn)
                return true;
            }

            // ── Cả hai sắp vào: so thời gian đến ──
            double myDistToCenter2  = staticDistanceToStopLine(self);
            if (myDistToCenter2 < 0) myDistToCenter2 = myDistToCenter; // đã qua vạch, dùng khoảng cách thực
            double mySpeed    = Math.max(self.getSpeed(),  10);
            double theirSpeed = Math.max(other.getSpeed(), 10);

            double myETA    = myDistToCenter2    / mySpeed;
            double theirETA = theirPathDistToCenter / theirSpeed;

            // Nếu xe kia đến sớm hơn hoặc cùng lúc (trong 1s) → nhường
            if (theirETA <= myETA + 1.0) return true;
        }

        return false;
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
     * Hai path (entry→exit) có giao nhau trong giao lộ không?
     * Dùng lookup table 4-way chuẩn giao thông.
     *
     * Quy tắc: A→B cắt C→D khi đường đi của A qua vùng mà C cần dùng.
     * Chỉ KHÔNG cắt khi: cùng entry, hoặc cùng exit + cùng entry arm bên,
     * hoặc rẽ phải không qua trung tâm.
     */
    private boolean pathsWillCross(String entA, String exA, String entB, String exB) {
        // Rẽ phải (right turn) không đi qua trung tâm → ít xung đột với nhau
        // nhưng vẫn có thể xung đột với xe đi thẳng từ hướng khác
        boolean aRightTurn = isRightTurn(entA, exA);
        boolean bRightTurn = isRightTurn(entB, exB);

        // Nếu cả hai đều rẽ phải → không cắt nhau
        if (aRightTurn && bRightTurn) return false;

        // Xe B đi ra cùng arm với xe A đi vào → B thoát khỏi vùng xe A vào → không giao
        if (exB.equals(entA)) return false;
        // Tương tự
        if (exA.equals(entB)) return false;

        // Trường hợp rẽ phải vs thẳng/trái từ hướng đối diện: rẽ phải nhanh, ít nguy hiểm
        if (aRightTurn && isOpposite(entA, entB)) return false;
        if (bRightTurn && isOpposite(entA, entB)) return false;

        // Mặc định: coi là có thể xung đột
        return true;
    }

    /**
     * Rẽ phải theo chiều lái phải (right-hand traffic).
     * N→E, E→S, S→W, W→N là rẽ phải.
     */
    private boolean isRightTurn(String entry, String exit) {
        if (entry.isEmpty() || exit.isEmpty()) return false;
        char en = entry.charAt(0), ex = exit.charAt(0);
        return (en == 'N' && ex == 'E') || (en == 'E' && ex == 'S')
            || (en == 'S' && ex == 'W') || (en == 'W' && ex == 'N');
    }

    /**
     * Ước tính khoảng cách dọc theo path từ vị trí hiện tại của xe đến một điểm đích.
     * Duyệt các waypoint còn lại, dừng khi đã đi qua điểm gần nhất với target.
     */
    private double pathDistanceToPoint(core.vehicle.Vehicle v, Vector2D target) {
        java.util.List<Vector2D> wps = v.getPath().getWaypoints();
        int cur = v.getWaypointIndex();
        if (cur >= wps.size()) return Double.MAX_VALUE;

        double dist = v.getEffectivePosition().distanceTo(wps.get(cur));
        double minDist = Double.MAX_VALUE;
        double traveled = 0;

        // Đoạn từ vị trí hiện tại đến waypoint đầu tiên
        double segLen = v.getEffectivePosition().distanceTo(wps.get(cur));
        double dToTarget = segClosestDist(v.getEffectivePosition(), wps.get(cur), target);
        if (dToTarget < minDist) {
            minDist = dToTarget;
            traveled = segLen * closestFraction(v.getEffectivePosition(), wps.get(cur), target);
        }
        double cumLen = segLen;

        for (int i = cur; i + 1 < wps.size(); i++) {
            double sLen = wps.get(i).distanceTo(wps.get(i + 1));
            dToTarget = segClosestDist(wps.get(i), wps.get(i + 1), target);
            if (dToTarget < minDist) {
                minDist = dToTarget;
                traveled = cumLen + sLen * closestFraction(wps.get(i), wps.get(i + 1), target);
            }
            cumLen += sLen;
        }
        return traveled;
    }

    /** Khoảng cách từ điểm p đến đoạn thẳng ab. */
    private double segClosestDist(Vector2D a, Vector2D b, Vector2D p) {
        Vector2D ab = b.subtract(a);
        double len2 = ab.dot(ab);
        if (len2 < 1e-9) return p.distanceTo(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
        Vector2D closest = a.add(ab.multiply(t));
        return p.distanceTo(closest);
    }

    /** Fraction [0,1] của điểm gần nhất trên đoạn ab tới p. */
    private double closestFraction(Vector2D a, Vector2D b, Vector2D p) {
        Vector2D ab = b.subtract(a);
        double len2 = ab.dot(ab);
        if (len2 < 1e-9) return 0;
        return Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
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
