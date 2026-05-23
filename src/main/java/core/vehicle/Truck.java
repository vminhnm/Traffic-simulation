package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.road.VehiclePath;
import sound.SoundType;

/**
 * <b>Xe tải</b> — phương tiện nặng nhất, dừng chậm nhất.
 *
 * <p>Đặc điểm:</p>
 * <ul>
 *   <li>Thân rất dài và rộng — chiếm nhiều không gian đường.</li>
 *   <li>Gia tốc và gia tốc hãm rất thấp (quán tính lớn).</li>
 *   <li>Tốc độ thấp nhất trong nhóm phương tiện thông thường.</li>
 * </ul>
 */
public class Truck extends Vehicle {

    public Truck(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("truck")
                .displayName("Xe tải")
                .basicLabel("Truck")
                .bodyColor(new Color(100, 100, 140))  // xanh xám
                .roofColor(new Color(70,  70, 110))
                .spritePath("assets/sprites/truck.png")
                .defaultMaxSpeed(55)
                .defaultAcceleration(15)        // gia tốc chậm
                .defaultLength(72)              // dài nhất trong mô phỏng
                .defaultWidth(20)
                .engineSound(SoundType.ENGINE_TRUCK)
                .hornSound(SoundType.HORN_LONG)
                .build();
    }

    @Override
    public boolean isPriorityVehicle() { return false; }
}
