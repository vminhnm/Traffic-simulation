package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import graphics.sprite.RenderAssetKey;
import sound.SoundType;

/**
 * <b>Xe đạp</b> — phương tiện chậm nhất, kích thước nhỏ nhất.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Tốc độ rất thấp → dễ bị vượt bởi các phương tiện khác.</li>
 *   <li>Không có âm thanh động cơ — dùng tiếng chuông xe đạp.</li>
 *   <li>Hình dạng thon nhỏ.</li>
 * </ul>
 */
public class Bicycle extends Vehicle {

    public Bicycle(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("bicycle")
                .displayName("Xe đạp")
                .basicLabel("Bike")
                .bodyColor(new Color(34, 170, 34))    // xanh lá tươi
                .roofColor(new Color(20, 120, 20))
                //.spritePath("assets/sprites/bicycle.png")
                .spriteKey(RenderAssetKey.BICYCLE_EAST)
                .defaultMaxSpeed(35)
                .defaultAcceleration(40)
                .defaultLength(25)
                .defaultWidth(10)
                .defaultRenderLength(147)
                .defaultRenderWidth(147)
                .engineSound(SoundType.BICYCLE_BELL)  // không có động cơ → dùng chuông
                .hornSound(SoundType.BICYCLE_BELL)
                .build();
    }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
