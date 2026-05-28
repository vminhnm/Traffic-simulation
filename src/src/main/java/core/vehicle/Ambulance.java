package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.driver.EmergencyDriver;
import core.road.VehiclePath;
import graphics.sprite.RenderAssetKey;
import sound.SoundType;

/**
 * <b>Xe cứu thương</b> — xe ưu tiên khẩn cấp.
 *
 * <p>Hành vi đặc biệt (kế thừa từ {@link PriorityVehicle}):</p>
 * <ul>
 *   <li>Mặc định dùng {@link EmergencyDriver} → bỏ qua đèn đỏ.</li>
 *   <li>Đèn cảnh báo nháy đỏ–xanh xen kẽ (2.5 Hz).</li>
 *   <li>Xe khác trong bán kính 150 px phải nhường đường.</li>
 *   <li>Thân màu trắng với chữ thập đỏ (chế độ Basic: "Ambu").</li>
 * </ul>
 *
 * <p>Nếu cần tắt còi (đã đến nơi): gọi {@code setSirenActive(false)}.</p>
 */
public class Ambulance extends PriorityVehicle {

    public Ambulance(String id, VehiclePath path) {
        // Xe cứu thương luôn dùng EmergencyDriver
        super(id, path, new EmergencyDriver());
    }

    /**
     * Constructor phụ: cho phép truyền DriverBehavior tùy chỉnh
     * (ví dụ NormalDriver khi xe đã tắt còi và đang về trạm).
     */
    public Ambulance(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("ambulance")
                .displayName("Xe cứu thương")
                .basicLabel("Ambu")
                .bodyColor(Color.WHITE)
                .roofColor(new Color(200, 0, 0))        // đỏ
                //.spritePath("assets/sprites/ambulance.png")
                .spriteKey(RenderAssetKey.AMBULANCE_EAST)
                .defaultMaxSpeed(100)
                .defaultAcceleration(60)
                .defaultLength(44)
                .defaultWidth(20)
                .defaultRenderLength(57)
                .defaultRenderWidth(57)
                .engineSound(SoundType.ENGINE_CAR)
                .hornSound(SoundType.HORN_LONG)
                .sirenSound(SoundType.AMBULANCE_SIREN)
                .build();
    }

    /**
     * Nhịp đèn nháy 2.5 Hz — nhanh hơn mặc định để truyền cảm giác khẩn cấp.
     */
    @Override
    protected double getSirenFlashRate() { return 2.5; }
}
