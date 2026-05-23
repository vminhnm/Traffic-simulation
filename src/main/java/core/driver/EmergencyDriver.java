package core.driver;

import core.rule.TrafficRuleEvaluator;
import core.simulation.SimulationWorld;
import core.vehicle.PriorityVehicle;
import core.vehicle.Vehicle;

/**
 * <b>Lái xe khẩn cấp</b> — dành riêng cho {@link core.vehicle.PriorityVehicle}.
 *
 * <h2>Đặc điểm</h2>
 * <ul>
 *   <li><b>Bỏ qua hoàn toàn đèn đỏ / vàng</b> — xe ưu tiên có quyền đi.</li>
 *   <li>Tốc độ = {@code maxSpeed × 1.4}.</li>
 *   <li>Khi có xe đang chắn thẳng đường ({@code gap < CRITICAL_GAP}),
 *       giảm tốc nhẹ thay vì phanh gấp — tránh đâm nhưng không dừng.</li>
 *   <li>Tín hiệu EMERGENCY_PASS thông báo cho SimulationEngine biết
 *       cần kích hoạt logic "xe thường tránh đường" cho vùng lân cận.</li>
 * </ul>
 */
public class EmergencyDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES         = new TrafficRuleEvaluator();
    private static final double               SPEED_FACTOR  = 1.4;
    /** Khoảng cách tối thiểu (px) trước khi bắt đầu giảm tốc nhẹ. */
    private static final double               CRITICAL_GAP  = 20.0;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        if (vehicle instanceof PriorityVehicle priorityVehicle
                && !priorityVehicle.isSirenActive()) {
            if (RULES.mustStopAtRedLight(vehicle, world)) {
                return DrivingDecision.stop();
            }

            double gap = RULES.gapToFrontVehicle(vehicle, world);
            double safeDistance = vehicle.getLength() * 1.5 + 15;
            if (gap >= 0 && gap < safeDistance) {
                double ratio = Math.max(0, gap / safeDistance);
                return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.6);
            }

            return DrivingDecision.accelerate(vehicle.getMaxSpeed());
        }

        // Kiểm tra khoảng trống vật lý phía trước (xe thường chưa kịp tránh)
        double gap = RULES.gapToFrontVehicle(vehicle, world);

        if (gap >= 0 && gap < CRITICAL_GAP) {
            // Nếu sát rạt (khoảng trống < 3px), bắt buộc dừng hẳn tránh đâm đuôi
            if (gap < 3.0) return DrivingDecision.stop();
            // Giảm tốc vừa phải để chờ xe thường nhường đường
            return DrivingDecision.brake(vehicle.getMaxSpeed() * 0.5);
        }

        return DrivingDecision.emergencyPass(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Emergency"; }
}
