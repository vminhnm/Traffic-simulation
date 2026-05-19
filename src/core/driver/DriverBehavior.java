package core.driver;

import core.simulation.SimulationWorld;
import core.vehicle.Vehicle;

/**
 * <b>Strategy — "Bộ não" lái xe.</b>
 *
 * <p>Mỗi implementation định nghĩa một phong cách lái khác nhau.
 * Để thêm phong cách mới, chỉ cần {@code implements DriverBehavior} —
 * <em>không sửa Vehicle hay TrafficController</em>.</p>
 *
 * <p>Các implementation có sẵn:</p>
 * <ul>
 *   <li>{@link NormalDriver}    – tuân thủ đèn, giữ khoảng cách</li>
 *   <li>{@link AggressiveDriver}– phóng nhanh, bỏ qua đèn vàng</li>
 *   <li>{@link EmergencyDriver} – xe ưu tiên, bỏ qua đèn đỏ</li>
 * </ul>
 */
public interface DriverBehavior {

    /**
     * Quan sát trạng thái của {@code vehicle} và thế giới {@code world},
     * trả về quyết định cần thực hiện ở bước cập nhật này.
     *
     * @param vehicle  phương tiện đang được điều khiển
     * @param world    toàn bộ trạng thái mô phỏng (xe khác, đèn, đường)
     * @return quyết định lái (ACCELERATE / BRAKE / STOP / YIELD / EMERGENCY_PASS)
     */
    DrivingDecision decide(Vehicle vehicle, SimulationWorld world);

    /**
     * Tên phong cách lái — hiển thị ở UI và dùng cho debug/thống kê.
     */
    String getStyleName();
}
