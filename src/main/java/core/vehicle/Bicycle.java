package core.vehicle;

<<<<<<< HEAD
import core.driver.DriverBehavior;
import core.road.VehiclePath;
import java.awt.Color;
=======
import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import graphics.sprite.RenderAssetKey;
>>>>>>> 11a269e5cac08a2da59c3057d2327b555271973e
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
<<<<<<< HEAD
                .spritePath("assets/sprites/bicycle.png")
                .defaultMaxSpeed(35)
                .defaultAcceleration(20)
                .defaultLength(16)
                .defaultWidth(7)
=======
                //.spritePath("assets/sprites/bicycle.png")
                .spriteKey(RenderAssetKey.BICYCLE_EAST)
                .defaultMaxSpeed(35)
                .defaultAcceleration(20)
                .defaultLength(50)
                .defaultWidth(35)
>>>>>>> 11a269e5cac08a2da59c3057d2327b555271973e
                .engineSound(SoundType.BICYCLE_BELL)  // không có động cơ → dùng chuông
                .hornSound(SoundType.BICYCLE_BELL)
                .build();
    }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
