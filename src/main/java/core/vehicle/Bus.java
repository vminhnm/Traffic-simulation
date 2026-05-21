package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import sound.SoundType;

/**
 * <b>Xe buýt</b> — phương tiện công cộng, kích thước lớn, tốc độ trung bình.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Thân dài → đòi hỏi khoảng cách dừng lớn hơn.</li>
 *   <li>Gia tốc chậm → phản ứng chậm khi đèn xanh.</li>
 *   <li>Màu vàng đặc trưng — dễ nhận diện trong mô phỏng.</li>
 * </ul>
 */
public class Bus extends Vehicle {

    public Bus(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("bus")
                .displayName("Xe buýt")
                .basicLabel("Bus")
                .bodyColor(new Color(255, 200, 0))    // vàng
                .roofColor(new Color(200, 150, 0))
                .spritePath("assets/sprites/bus.png")
                .defaultMaxSpeed(65)
                .defaultAcceleration(22)
                .defaultLength(60)
                .defaultWidth(22)
                .engineSound(SoundType.ENGINE_BUS)
                .hornSound(SoundType.HORN_LONG)
                .build();
    }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
