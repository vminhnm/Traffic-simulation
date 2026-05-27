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
    /**
     * Khoảng cách (px) để coi là "đã vào vạch dừng" — không còn quay lại được.
     * Tăng lên 30px (từ 10px) để xe đã vào giao lộ không bị bắt dừng lại,
     * tránh tình trạng xe bị chặn đứng giữa đường gây va chạm với xe luồng khác.
     */
    private static final double COMMITTED_DISTANCE   = 30.0;

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
        // Nếu đã vượt qua vạch dừng → không dừng lại nữa (đã committed)
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
            return vehiclePos.distanceTo(stopPos) - vehicle.getLength() / 2.0;
        }

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
     * Khoảng cách (px) từ đầu xe hiện tại đến đuôi xe gần nhất phía trước.
     *
     * <p><b>Nguyên tắc cross-path:</b><br>
     * Xe từ luồng khác chỉ bị tính là vật cản khi:</p>
     * <ol>
     *   <li>Nó đang đứng chờ đèn (isStopped) — nghĩa là đang ở phía trước vạch dừng,
     *       nên xe hiện tại cũng phải dừng xếp hàng.</li>
     *   <li>HOẶC xe hiện tại chưa vào giao lộ (chưa qua stopIndex) — cần phát hiện
     *       xe đang băng ngang trước mặt để tránh đâm.</li>
     * </ol>
     * Khi xe hiện tại đã vào giao lộ (đã qua stopIndex), nó KHÔNG dừng lại vì
     * xe luồng khác — điều đó sẽ gây kẹt giữa giao lộ và tạo deadlock.
     *
     * <p><b>Chống deadlock:</b> Nếu 2 xe cùng mutual-blocking, xe ID nhỏ hơn được đi trước.</p>
     *
     * @return khoảng cách dương nếu có xe trước; -1 nếu đường trống.
     */
    public double gapToFrontVehicle(Vehicle self, SimulationWorld world) {
        String pathId = self.getPath().getId();
        Vector2D pos  = self.getPosition();

        // Lateral distance threshold: vehicles on adjacent parallel lanes should not block each other
        // (their path IDs differ by lane index but they share the same road segment)
        final double SAME_LANE_LATERAL_THRESHOLD = 30.0; // px — same lane if within this lateral distance

        // ── 1. Xe cùng path ──────────────────────────────────────────
        double samePath = world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> v.getPath().getId().equals(pathId))
                .filter(v -> v.getWaypointIndex() >= self.getWaypointIndex())
                .filter(v -> isAheadOnPath(self, v))
                // Only count vehicles that are laterally close (same lane, not the adjacent lane)
                .filter(v -> isLaterallySameLane(self, v, SAME_LANE_LATERAL_THRESHOLD))
                .min(Comparator.comparingDouble(v -> v.getPosition().distanceTo(pos)))
                .map(front -> {
                    double centerDist = front.getPosition().distanceTo(pos);
                    return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
                })
                .orElse(-1.0);

        // ── 2. Xe khác path (cross-path) ─────────────────────────────
        // Xe hiện tại đã vào giao lộ (qua stopIndex) → chỉ quan tâm cùng path,
        // không dừng vì xe luồng khác (tránh kẹt giữa giao lộ).
        boolean selfInsideIntersection = self.getWaypointIndex() > self.getPath().getStopIndex();

        final double CROSS_CHECK_RADIUS = 100.0; // tăng từ 80 lên 100px để phát hiện sớm hơn
        double crossPath = -1.0;

        if (!selfInsideIntersection) {
            // Xe chưa vào giao lộ: phát hiện xe đang băng ngang phía trước
            crossPath = world.getVehicles().stream()
                    .filter(v -> v != self)
                    .filter(v -> !v.getPath().getId().equals(pathId))
                    .filter(v -> isAheadOnPath(self, v))
                    // Chỉ tính xe đang dừng chờ đèn HOẶC đang trong giao lộ (băng ngang)
                    .filter(v -> v.isStopped() || v.getWaypointIndex() > v.getPath().getStopIndex())
                    // Loại trừ mutual blocking deadlock
                    .filter(v -> !isMutuallyBlocking(self, v, world))
                    .filter(v -> v.getPosition().distanceTo(pos) < CROSS_CHECK_RADIUS)
                    .min(Comparator.comparingDouble(v -> v.getPosition().distanceTo(pos)))
                    .map(front -> {
                        double centerDist = front.getPosition().distanceTo(pos);
                        return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
                    })
                    .orElse(-1.0);
        }

        if (samePath < 0) return crossPath;
        if (crossPath < 0) return samePath;
        return Math.min(samePath, crossPath);
    }

    /**
     * Kiểm tra 2 xe có đang mutual-blocking (deadlock chéo) không.
     * Xe ID nhỏ hơn được ưu tiên đi → xe ID lớn hơn trả về true (nhường đường).
     */
    private boolean isMutuallyBlocking(Vehicle self, Vehicle other, SimulationWorld world) {
        final double DEADLOCK_RADIUS = 80.0;
        double dist = self.getPosition().distanceTo(other.getPosition());
        if (dist >= DEADLOCK_RADIUS) return false;

        boolean otherAlsoBlockedBySelf = isAheadOnPath(other, self);
        if (!otherAlsoBlockedBySelf) return false;

        // self.id nhỏ hơn → self được đi → loại bỏ other khỏi danh sách cản
        int cmp = self.getId().compareTo(other.getId());
        return cmp < 0;
    }

    /**
     * Returns true if other vehicle is laterally close enough to self
     * to be considered in the same lane (not an adjacent parallel lane).
     */
    private boolean isLaterallySameLane(Vehicle self, Vehicle other, double threshold) {
        Vector2D selfDir = self.getVelocity();
        if (selfDir.length() < 1e-9) {
            int nextIdx = self.getWaypointIndex();
            if (nextIdx < self.getPath().getWaypoints().size()) {
                selfDir = self.getPath().getWaypoints().get(nextIdx).subtract(self.getPosition());
            } else {
                return true; // can't tell, assume same lane
            }
        }
        Vector2D normalizedDir = selfDir.normalize();
        Vector2D toOther = other.getPosition().subtract(self.getPosition());
        // Lateral distance = magnitude of component perpendicular to travel direction
        double along = normalizedDir.dot(toOther);
        double lateralSq = toOther.length() * toOther.length() - along * along;
        double lateral = lateralSq > 0 ? Math.sqrt(lateralSq) : 0;
        return lateral < threshold;
    }

    /**
     * {@code other} có ở phía trước {@code self} theo hướng di chuyển không?
     */
    private boolean isAheadOnPath(Vehicle self, Vehicle other) {
        Vector2D dir = self.getVelocity();
        if (dir.length() < 1e-9) {
            int nextIdx = self.getWaypointIndex();
            if (nextIdx < self.getPath().getWaypoints().size()) {
                dir = self.getPath().getWaypoints().get(nextIdx)
                         .subtract(self.getPosition());
            } else {
                return false;
            }
        }
        Vector2D toOther = other.getPosition().subtract(self.getPosition());
        return dir.normalize().dot(toOther.normalize()) > 0.5;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Xe ưu tiên
    // ─────────────────────────────────────────────────────────────────

    public boolean shouldYieldToPriorityVehicle(Vehicle self, SimulationWorld world) {
        if (self.isPriorityVehicle()) return false;

        return world.getVehicles().stream()
                .filter(v -> v instanceof PriorityVehicle pv && pv.isSirenActive())
                .map(v -> (PriorityVehicle) v)
                .anyMatch(pv -> {
                    double dist = pv.getPosition().distanceTo(self.getPosition());
                    return dist <= pv.getSirenRadius();
                });
    }

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

    public boolean isColliding(Vehicle v1, Vehicle v2) {
        Area area1 = getBoundingBox(v1);
        Area area2 = getBoundingBox(v2);
        area1.intersect(area2);
        return !area1.isEmpty();
    }

    private Area getBoundingBox(Vehicle v) {
        Rectangle2D.Double rect = new Rectangle2D.Double(
                -v.getLength() / 2, -v.getWidth() / 2, v.getLength(), v.getWidth());
        AffineTransform transform = new AffineTransform();
        transform.translate(v.getPosition().x, v.getPosition().y);
        transform.rotate(v.getRotation());
        return new Area(new Path2D.Double(rect, transform));
    }
}
