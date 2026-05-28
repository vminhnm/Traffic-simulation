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

        return findFrontVehicle(self, world)
                .map(front -> {
                    double centerDist = front.getEffectivePosition().distanceTo(pos);
                    return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
                })
                .orElse(-1.0);
    }

    public Optional<Vehicle> findFrontVehicle(Vehicle self, SimulationWorld world) {
        Vector2D pos  = self.getEffectivePosition();

        return world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> !v.isFinished() && !v.isCrashed())
                .filter(v -> isAheadInSameLane(self, v))
                .min(Comparator.comparingDouble(v -> v.getEffectivePosition().distanceTo(pos)));
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

    public boolean hasSlowFrontVehicle(Vehicle self, SimulationWorld world) {
        double gap = gapToFrontVehicle(self, world);
        if (gap < 0 || gap > Math.max(120.0, self.getLength() * 3.0)) return false;

        return findFrontVehicle(self, world)
                .map(front -> front.getSpeed() < self.getMaxSpeed() * 0.65)
                .orElse(false);
    }

    public boolean canOvertakeLeft(Vehicle self, SimulationWorld world) {
        return isLateralPathClear(self, world, -50.0);
    }

    public boolean canOvertakeRight(Vehicle self, SimulationWorld world) {
        return isLateralPathClear(self, world, 50.0);
    }

    public boolean canYieldRight(Vehicle self, SimulationWorld world) {
        return isLateralPathClear(self, world, 50.0);
    }

    public boolean canYieldLeft(Vehicle self, SimulationWorld world) {
        return isLateralPathClear(self, world, -50.0);
    }

    public boolean isLateralPathClear(Vehicle self, SimulationWorld world, double targetOffset) {
        double currentOffset = self.getLateralOffset();
        double midOffset = (currentOffset + targetOffset) / 2.0;
        return isProjectedVehicleClear(self, world, midOffset)
                && isProjectedVehicleClear(self, world, targetOffset);
    }

    private boolean isProjectedVehicleClear(Vehicle self, SimulationWorld world, double lateralOffset) {
        Area sideArea = getBoundingBox(self, centerAtLateralOffset(self, lateralOffset));
        Vector2D forward = movementDirection(self);
        Area forwardSideArea = getBoundingBox(
                self,
                centerAtLateralOffset(self, lateralOffset)
                        .add(forward.multiply(self.getLength() * 0.75)));

        for (Vehicle other : world.getVehicles()) {
            if (other == self || other.isFinished() || other.isCrashed()) continue;

            Area otherArea = getBoundingBox(other);
            Area overlap = new Area(sideArea);
            overlap.intersect(otherArea);
            if (!overlap.isEmpty()) return false;

            Area forwardOverlap = new Area(forwardSideArea);
            forwardOverlap.intersect(otherArea);
            if (!forwardOverlap.isEmpty()) return false;
        }

        return true;
    }

    private Vector2D centerAtLateralOffset(Vehicle vehicle, double lateralOffset) {
        Vector2D rightVector = new Vector2D(
                Math.cos(vehicle.getRotation() + Math.PI / 2.0),
                Math.sin(vehicle.getRotation() + Math.PI / 2.0));
        return vehicle.getPosition().add(rightVector.multiply(lateralOffset));
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

    /**
     * Kiểm tra xem hai xe có đang va chạm vật lý không (có tính đến góc xoay).
     */
    public boolean isColliding(Vehicle v1, Vehicle v2) {
        Area area1 = getBoundingBox(v1);
        Area area2 = getBoundingBox(v2);
        
        area1.intersect(area2);
        return !area1.isEmpty(); // Nếu không rỗng nghĩa là có phần giao nhau (va chạm)
    }

    private Area getBoundingBox(Vehicle v) {
        return getBoundingBox(v, v.getEffectivePosition());
    }

    private Area getBoundingBox(Vehicle v, Vector2D center) {
        // Tạo hình chữ nhật với tâm ở (0, 0)
        Rectangle2D.Double rect = new Rectangle2D.Double(
                -v.getLength() / 2, -v.getWidth() / 2, v.getLength(), v.getWidth());
        // Tịnh tiến và xoay
        AffineTransform transform = new AffineTransform();
        transform.translate(center.x, center.y);
        transform.rotate(v.getRotation());
        return new Area(new Path2D.Double(rect, transform));
    }
}
