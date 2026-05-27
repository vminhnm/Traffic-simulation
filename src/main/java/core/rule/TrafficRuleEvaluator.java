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
     * Finds the nearest vehicle directly ahead of {@code self}, considering
     * both same-path vehicles and cross-path vehicles in the intersection.
     *
     * @return the nearest blocking vehicle, or empty if the path is clear.
     */
    public Optional<Vehicle> frontVehicle(Vehicle self, SimulationWorld world) {
        String pathId = self.getPath().getId();
        Vector2D pos  = self.getPosition();

        // ── 1. Same path ─────────────────────────────────────────────
        Optional<Vehicle> samePath = world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> v.getPath().getId().equals(pathId))
                .filter(v -> v.getWaypointIndex() >= self.getWaypointIndex())
                .filter(v -> isAheadOnPath(self, v))
                .min(Comparator.comparingDouble(v -> v.getPosition().distanceTo(pos)));

        // ── 2. Cross-path ─────────────────────────────────────────────
        // Only checked when self has not yet entered the intersection.
        boolean selfInsideIntersection = self.getWaypointIndex() > self.getPath().getStopIndex();
        Optional<Vehicle> crossPath = Optional.empty();

        if (!selfInsideIntersection) {
            crossPath = world.getVehicles().stream()
                    .filter(v -> v != self)
                    .filter(v -> !v.getPath().getId().equals(pathId))
                    // Include vehicles actively crossing the intersection OR stopped
                    // vehicles that are physically close enough to be a real obstacle
                    // (fixes the case where two cars queue toward the same turning point).
                    .filter(v -> v.getWaypointIndex() > v.getPath().getStopIndex()
                              || (v.isStopped() && v.getPosition().distanceTo(pos) < self.getLength() * 3.0))
                    .filter(v -> isAheadOnPathNarrow(self, v))
                    .filter(v -> !isMutuallyBlocking(self, v, world))
                    .filter(v -> v.getPosition().distanceTo(pos) < 100.0)
                    .min(Comparator.comparingDouble(v -> v.getPosition().distanceTo(pos)));
        }

        if (samePath.isEmpty()) return crossPath;
        if (crossPath.isEmpty()) return samePath;
        // Return whichever is closer
        double dSame  = samePath.get().getPosition().distanceTo(pos);
        double dCross = crossPath.get().getPosition().distanceTo(pos);
        return dSame <= dCross ? samePath : crossPath;
    }

    /**
     * Khoảng cách (px) từ đầu xe hiện tại đến đuôi xe gần nhất phía trước.
     *
     * @return khoảng cách dương nếu có xe trước; -1 nếu đường trống.
     */
    public double gapToFrontVehicle(Vehicle self, SimulationWorld world) {
        return frontVehicle(self, world).map(front -> {
            double centerDist = front.getPosition().distanceTo(self.getPosition());
            return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
        }).orElse(-1.0);
    }

    /**
     * Speed (px/s) of the vehicle directly ahead of {@code self}.
     * Returns 0.0 if there is no vehicle ahead (treat as stationary obstacle).
     */
    public double frontVehicleSpeed(Vehicle self, SimulationWorld world) {
        return frontVehicle(self, world)
                .map(Vehicle::getSpeed)
                .orElse(0.0);
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
     * {@code other} có ở phía trước {@code self} theo hướng di chuyển không?
     * Uses the waypoint direction when the vehicle is slow or stopped, since
     * velocity is unreliable at low speeds.
     */
    private boolean isAheadOnPath(Vehicle self, Vehicle other) {
        Vector2D dir = self.getVelocity();
        // Use waypoint direction when moving slowly or stopped
        if (dir.length() < self.getMaxSpeed() * 0.1) {
            int nextIdx = self.getWaypointIndex();
            if (nextIdx < self.getPath().getWaypoints().size()) {
                dir = self.getPath().getWaypoints().get(nextIdx)
                         .subtract(self.getPosition());
            } else {
                return false;
            }
        }
        Vector2D toOther = other.getPosition().subtract(self.getPosition());
        if (toOther.length() < 1e-9) return true; // same position = definitely blocking
        return dir.normalize().dot(toOther.normalize()) > 0.5;
    }

    /**
     * Stricter version for cross-path checks: requires the other vehicle to be
     * within a narrow forward cone (dot > 0.95, i.e. ~18°) to avoid false positives
     * from vehicles on adjacent parallel lanes.
     */
    private boolean isAheadOnPathNarrow(Vehicle self, Vehicle other) {
        Vector2D dir = self.getVelocity();
        if (dir.length() < self.getMaxSpeed() * 0.1) {
            int nextIdx = self.getWaypointIndex();
            if (nextIdx < self.getPath().getWaypoints().size()) {
                dir = self.getPath().getWaypoints().get(nextIdx)
                         .subtract(self.getPosition());
            } else {
                return false;
            }
        }
        Vector2D toOther = other.getPosition().subtract(self.getPosition());
        if (toOther.length() < 1e-9) return false;
        return dir.normalize().dot(toOther.normalize()) > 0.95;
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