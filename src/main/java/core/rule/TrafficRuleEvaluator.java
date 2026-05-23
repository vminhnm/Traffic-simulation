package core.rule;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Comparator;
import java.util.Optional;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import core.simulation.SimulationWorld;
import core.trafficlight.LightColor;
import core.trafficlight.TrafficLight;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;
import util.Vector2D;

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
     * trên cùng đường đi.
     *
     * @return khoảng cách dương nếu có xe trước; -1 nếu đường trống.
     */
    public double gapToFrontVehicle(Vehicle self, SimulationWorld world) {
        String pathId = self.getPath().getId();
        Vector2D pos  = self.getPosition();

        return world.getVehicles().stream()
                .filter(v -> v != self)
                .filter(v -> v.getPath().getId().equals(pathId))
                // Xe phía trước: đang tiến đến waypoint >= waypoint của self,
                // hoặc gần hơn stop-line.
                .filter(v -> v.getWaypointIndex() >= self.getWaypointIndex())
                .filter(v -> isAheadOnPath(self, v))
                .min(Comparator.comparingDouble(v -> v.getPosition().distanceTo(pos)))
                .map(front -> {
                    double centerDist = front.getPosition().distanceTo(pos);
                    return centerDist - front.getLength() / 2.0 - self.getLength() / 2.0;
                })
                .orElse(-1.0);
    }

    /**
     * {@code other} có ở phía trước {@code self} theo hướng di chuyển không?
     */
    private boolean isAheadOnPath(Vehicle self, Vehicle other) {
        Vector2D dir      = self.getVelocity();
        if (dir.length() < 1e-9) {
            // Xe đứng yên → dùng hướng đến waypoint kế
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
                    if (dist > pv.getSirenRadius()) return false;

                    // Lấy hướng di chuyển của xe hiện tại (nếu đứng yên thì lấy theo path)
                    Vector2D selfDir = self.getVelocity();
                    if (selfDir.length() < 1e-9) {
                        int nextIdx = self.getWaypointIndex();
                        if (nextIdx < self.getPath().getWaypoints().size()) {
                            selfDir = self.getPath().getWaypoints().get(nextIdx).subtract(self.getPosition());
                        }
                    }

                    // Lấy hướng di chuyển của xe ưu tiên
                    Vector2D pvDir = pv.getVelocity();
                    if (pvDir.length() < 1e-9) {
                        int nextIdx = pv.getWaypointIndex();
                        if (nextIdx < pv.getPath().getWaypoints().size()) {
                            pvDir = pv.getPath().getWaypoints().get(nextIdx).subtract(pv.getPosition());
                        }
                    }

                    // 1. Không nhường nếu xe đang đi ngược chiều hoàn toàn (ví dụ: ở làn đối diện)
                    if (selfDir.length() > 1e-9 && pvDir.length() > 1e-9) {
                        double dot = selfDir.normalize().dot(pvDir.normalize());
                        if (dot < -0.5) return false;
                    }

                    // 2. Không nhường nếu xe hiện tại đã nằm ở phía sau đuôi xe ưu tiên
                    if (pvDir.length() > 1e-9) {
                        Vector2D toSelf = self.getPosition().subtract(pv.getPosition());
                        if (toSelf.length() > 1e-9 && pvDir.normalize().dot(toSelf.normalize()) < -0.2) {
                            return false;
                        }
                    }

                    return true;
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

    /**
     * Kiểm tra xem hai xe có đang va chạm vật lý không (có tính đến góc xoay).
     */
    public boolean isColliding(Vehicle v1, Vehicle v2) {
        // 1. Lọc nhanh: Bỏ qua tốn kém toán học nếu 2 xe ở quá xa nhau
        double dist = v1.getPosition().distanceTo(v2.getPosition());
        if (dist > (v1.getLength() + v2.getLength())) return false;

        // 2. Bỏ qua va chạm khi 2 xe đi ngược chiều (tránh va chạm ảo khi đi qua nhau ở 2 làn sát cạnh)
        Vector2D dir1 = getExpectedDirection(v1);
        Vector2D dir2 = getExpectedDirection(v2);
        if (dir1.length() > 1e-9 && dir2.length() > 1e-9) {
            // Nới lỏng góc thành > 113 độ (< -0.4). Giúp xử lý an toàn các đoạn đường cong làm xe chệch hướng
            if (dir1.normalize().dot(dir2.normalize()) < -0.4) {
                return false;
            }
        }

        Area area1 = getBoundingBox(v1);
        Area area2 = getBoundingBox(v2);
        
        area1.intersect(area2);
        return !area1.isEmpty(); // Nếu không rỗng nghĩa là có phần giao nhau (va chạm)
    }

    private Vector2D getExpectedDirection(Vehicle v) {
        Vector2D dir = v.getVelocity();
        if (dir.length() < 1e-9) { // Nếu xe đang dừng đèn đỏ (velocity = 0), lấy hướng dựa vào waypoint tiếp theo
            int nextIdx = v.getWaypointIndex();
            if (nextIdx < v.getPath().getWaypoints().size()) {
                dir = v.getPath().getWaypoints().get(nextIdx).subtract(v.getPosition());
            }
        }
        return dir;
    }

    private Area getBoundingBox(Vehicle v) {
        // Thu nhỏ hitbox (đặc biệt là chiều rộng) để tránh va chạm ảo khi 2 xe đi sát nhau
        double hitboxLength = v.getLength() * 0.85; 
        double hitboxWidth = v.getWidth() * 0.45; // Ép siêu nhỏ chiều rộng hông xe (còn 45%)

        // Tạo hình chữ nhật với tâm ở (0, 0)
        Rectangle2D.Double rect = new Rectangle2D.Double(
                -hitboxLength / 2, -hitboxWidth / 2, hitboxLength, hitboxWidth);
        // Tịnh tiến và xoay
        AffineTransform transform = new AffineTransform();
        transform.translate(v.getPosition().x, v.getPosition().y);
        transform.rotate(v.getRotation());
        return new Area(new Path2D.Double(rect, transform));
    }
}
