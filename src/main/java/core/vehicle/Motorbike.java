package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import graphics.sprite.RenderAssetKey;
import sound.SoundType;

/**
 * <b>Xe máy</b> — nhanh hơn và nhỏ hơn ô tô, dễ len lỏi trong lưu lượng đông.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Kích thước nhỏ → khoảng cách an toàn ngắn hơn.</li>
 *   <li>Tốc độ tối đa cao hơn ô tô.</li>
 *   <li>Gia tốc nhanh hơn (phản ứng nhanh khi đèn xanh).</li>
 *   <li>Override {@code speedMultiplier} để phản ánh sự linh hoạt.</li>
 * </ul>
 */
public class Motorbike extends Vehicle {

    public Motorbike(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("motorbike")
                .displayName("Xe máy")
                .basicLabel("Moto")
                .bodyColor(new Color(255, 140, 0))   // cam
                .roofColor(new Color(180, 90, 0))
                //.spritePath("assets/sprites/motorbike.png")
                .spriteKey(RenderAssetKey.MOTORBIKE_EAST)
                .defaultMaxSpeed(110)
                .defaultAcceleration(70)
                .defaultLength(30)
                .defaultWidth(15)
                .defaultRenderLength(60)
                .defaultRenderWidth(40)
                .engineSound(SoundType.ENGINE_MOTORBIKE)
                .hornSound(SoundType.HORN_SHORT)
                .build();
    }

    /**
     * Xe máy len lỏi tốt hơn → có thể tiến sát hơn 10%
     * trước khi bắt đầu hãm.
     */
    @Override
    protected double speedMultiplier() { return 1.1; }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
