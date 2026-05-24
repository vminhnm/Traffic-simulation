package core.vehicle;

import java.awt.Color;

import core.driver.DriverBehavior;
import core.driver.EmergencyDriver;
import core.road.VehiclePath;
import sound.SoundType;

/**
 * <b>Xe cứu hỏa</b> — xe ưu tiên khẩn cấp, kích thước lớn hơn xe cứu thương.
 *
 * <p>Hành vi đặc biệt (kế thừa từ {@link PriorityVehicle}):</p>
 * <ul>
 *   <li>Thân dài và rộng hơn Ambulance.</li>
 *   <li>Đèn cảnh báo nháy đỏ–trắng (3 Hz — nhanh nhất).</li>
 *   <li>Bán kính sirên lớn hơn (200 px) — xe xung quanh phải nhường sớm hơn.</li>
 *   <li>Thân màu đỏ rực đặc trưng.</li>
 * </ul>
 */
public class FireTruck extends PriorityVehicle {

    public FireTruck(String id, VehiclePath path) {
        super(id, path, new EmergencyDriver());
    }

    public FireTruck(String id, VehiclePath path, DriverBehavior driverBehavior) {
        super(id, path, driverBehavior);
    }

    @Override
    protected VehicleProfile buildProfile() {
        return VehicleProfile.builder("firetruck")
                .displayName("Xe cứu hỏa")
                .basicLabel("Fire")
                .bodyColor(new Color(210, 30, 30))      // đỏ rực
                .roofColor(new Color(160, 10, 10))
                .spritePath("assets/sprites/firetruck.png")
                .defaultMaxSpeed(100)
                .defaultAcceleration(40)
                .defaultLength(58)
                .defaultWidth(24)
                .engineSound(SoundType.ENGINE_TRUCK)
                .hornSound(SoundType.HORN_LONG)
                .sirenSound(SoundType.FIRE_TRUCK_SIREN)
                .build();
    }

    /** 3 Hz — nhịp đèn nhanh nhất, dễ phân biệt với Ambulance. */
    @Override
    protected double getSirenFlashRate() { return 3.0; }

    /** Xe cứu hỏa phát tín hiệu xa hơn: 200 px. */
    @Override
    public double getSirenRadius() { return 200.0; }
}
