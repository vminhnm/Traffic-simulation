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
 *   <li>Bỏ qua đèn đỏ / vàng.</li>
 *   <li>Warm-up 1.5 giây đầu: chạy chậm để xe trước kịp dạt ra.</li>
 *   <li>Dùng {@code gapToFrontVehicleOnPath} — bỏ qua xe đã dạt ra ngoài (offset ≥ 9px)
 *       để không bị kìm tốc độ bởi xe đã nhường đường.</li>
 *   <li>Phanh sớm dựa trên quãng đường phanh thực tế (v²/2a).</li>
 * </ul>
 */
public class EmergencyDriver implements DriverBehavior {

    private static final TrafficRuleEvaluator RULES        = new TrafficRuleEvaluator();
    private static final double               SPEED_FACTOR = 1.4;
    private static final double               WARMUP_DURATION = 1.5; // giây

    private double aliveTime = 0.0;

    @Override
    public DrivingDecision decide(Vehicle vehicle, SimulationWorld world) {

        // ── Siren tắt: hành xử như xe thường ────────────────────────
        if (vehicle instanceof PriorityVehicle pv && !pv.isSirenActive()) {
            if (RULES.mustStopAtRedLight(vehicle, world)) return DrivingDecision.stop();
            double gap = RULES.gapToFrontVehicle(vehicle, world);
            double safeDistance = vehicle.getLength() * 1.5 + 15;
            if (gap >= 0 && gap < safeDistance) {
                double ratio = Math.max(0, gap / safeDistance);
                return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.6);
            }
            return DrivingDecision.accelerate(vehicle.getMaxSpeed());
        }

        aliveTime += 0.016;

        // Chỉ tính xe còn đứng chắn đường thực sự (đã dạt thì bỏ qua)
        double gap   = RULES.gapToFrontVehicleOnPath(vehicle, world);
        double speed = vehicle.getSpeed();
        double accel = vehicle.getAcceleration();

        if (gap >= 0) {
            double brakingDist  = (speed * speed) / (accel * 2.0);
            double safeDistance = vehicle.getLength() * 1.5 + brakingDist + 10;

            if (gap < vehicle.getLength() * 0.6) {
                return DrivingDecision.stop();
            }
            if (gap < safeDistance) {
                double ratio = Math.max(0.0, gap / safeDistance);
                return DrivingDecision.brake(vehicle.getMaxSpeed() * ratio * 0.7);
            }
        }

        // Warm-up: chạy chậm để xe trước kịp dạt
        if (aliveTime < WARMUP_DURATION) {
            return DrivingDecision.emergencyPass(vehicle.getMaxSpeed() * 0.4);
        }

        return DrivingDecision.emergencyPass(vehicle.getMaxSpeed() * SPEED_FACTOR);
    }

    @Override
    public String getStyleName() { return "Emergency"; }
}
