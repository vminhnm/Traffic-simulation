package core.vehicle;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import sound.SoundManager;
import sound.SoundType;

/**
 * <b>Lớp trừu tượng trung gian cho xe ưu tiên.</b>
 *
 * <p>Bổ sung so với {@link Vehicle}:</p>
 * <ul>
 *   <li>Đèn cảnh báo nhấp nháy (siren flash) với tốc độ riêng.</li>
 *   <li>Bán kính phát tín hiệu sirên — xe thường trong bán kính này
 *       sẽ nhận được lệnh nhường đường từ {@code TrafficRuleEvaluator}.</li>
 *   <li>Override {@link #speedMultiplier()} để chạy nhanh hơn mặc định.</li>
 *   <li>Luôn trả về {@code true} cho {@link #isPriorityVehicle()}.</li>
 * </ul>
 *
 * <p>Lớp con chỉ cần ghi đè {@link #buildProfile()} và
 * {@link #getSirenFlashRate()} là đủ.</p>
 */
public abstract class PriorityVehicle extends Vehicle {

    /** Bán kính (px) mà xe ưu tiên phát tín hiệu — xe trong vùng này phải nhường. */
    private static final double DEFAULT_SIREN_RADIUS = 150.0;

    /**
     * true khi còi hú đang bật; false khi tắt (ví dụ xe cứu thương đã
     * đến nơi và tắt còi). Mặc định bật khi vừa tạo.
     */
    private boolean sirenActive = true;

    protected PriorityVehicle(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Đèn nháy / còi
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tốc độ nhấp nháy đèn cảnh báo (chu kỳ/giây).
     * Mặc định 2 Hz (sáng–tắt–sáng–tắt mỗi giây).
     * Lớp con override để tạo pattern khác (ví dụ xe cảnh sát nháy nhanh hơn).
     */
    protected double getSirenFlashRate() { return 2.0; }

    /** Bán kính sirên (px). Override để thay đổi. */
    public double getSirenRadius() { return DEFAULT_SIREN_RADIUS; }

    /** Bật/tắt còi. */
    public void setSirenActive(boolean active) { this.sirenActive = active; }
    public boolean isSirenActive()             { return sirenActive; }

    // ─────────────────────────────────────────────────────────────────
    //  Ghi đè từ Vehicle
    // ─────────────────────────────────────────────────────────────────

    /** Xe ưu tiên chạy nhanh hơn 20% so với maxSpeed danh nghĩa. */
    @Override
    protected double speedMultiplier() { return 1.2; }

    @Override
    public final boolean isPriorityVehicle() { return true; }

    /**
     * Override hiệu ứng: đèn nháy theo {@link #getSirenFlashRate()},
     * không theo chu kỳ cứng 0.5 s của Vehicle cha.
     */
    @Override
    protected void updateEffects(double deltaTime) {
        if (!sirenActive) {
            flashState = false;
            return;
        }
        flashTimer += deltaTime;
        double halfPeriod = 1.0 / (getSirenFlashRate() * 2);
        if (flashTimer >= halfPeriod) {
            flashTimer = 0;
            flashState = !flashState;
        }
    }
}
