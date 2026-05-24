package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import graphics.sprite.RenderAssetKey;
import sound.SoundType;

/**
 * <b>Ô tô cá nhân</b> — phương tiện phổ biến nhất trong mô phỏng.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Kích thước trung bình, tốc độ trung bình.</li>
 *   <li>Tuân thủ luật giao thông theo DriverBehavior được gán.</li>
 *   <li>Màu thân ngẫu nhiên trong bảng màu được định nghĩa sẵn.</li>
 * </ul>
 */
public class Car extends Vehicle {

    /** Bảng màu xe con để tạo sự đa dạng trực quan. */
    private static final Color[] BODY_COLORS = {
        new Color(41,  98, 255),   // xanh dương đậm
        new Color(220, 50,  50),   // đỏ
        new Color(50, 180,  50),   // xanh lá
        new Color(180, 120,  0),   // vàng đồng
        new Color(80,  80,  80),   // xám đậm
        new Color(200, 200, 200),  // bạc
        new Color(120,  0, 160),   // tím
    };

    private static int colorIndex = 0;

    public Car(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        Color body = BODY_COLORS[colorIndex % BODY_COLORS.length];
        colorIndex++;
        return VehicleProfile.builder("car")
                .displayName("Ô tô cá nhân")
                .basicLabel("Car")
                .bodyColor(body)
                .roofColor(body.darker())
                //.spritePath("assets/sprites/car.png")
                .spriteKey(RenderAssetKey.CAR_EAST)
                .defaultMaxSpeed(90)        // px/s
                .defaultAcceleration(45)    // px/s²
                .defaultLength(36)          // px
                .defaultWidth(18)           // px
                .engineSound(SoundType.ENGINE_CAR)
                .hornSound(SoundType.HORN_SHORT)
                .build();
    }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
