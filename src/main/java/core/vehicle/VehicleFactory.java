package core.vehicle;

import core.driver.*;
import core.road.VehiclePath;
import util.IdGenerator;

/**
 * <b>Factory — tạo phương tiện theo loại.</b>
 *
 * <p>Caller chỉ cần biết {@code typeKey} (ví dụ {@code "car"}, {@code "ambulance"})
 * và đường đi. Factory tự chọn lớp cụ thể và DriverBehavior mặc định phù hợp.
 * Để thêm loại xe mới: thêm một {@code case} vào {@link #create} — không đụng
 * đến TrafficController hay SimulationEngine.</p>
 *
 * <h2>typeKey hợp lệ</h2>
 * <pre>
 *   "car"        → Car          + NormalDriver
 *   "motorbike"  → Motorbike    + NormalDriver
 *   "bicycle"    → Bicycle      + NormalDriver
 *   "bus"        → Bus          + NormalDriver
 *   "truck"      → Truck        + NormalDriver
 *   "ambulance"  → Ambulance    + EmergencyDriver
 *   "firetruck"  → FireTruck    + EmergencyDriver
 * </pre>
 *
 * <p>Có thể chỉ định DriverBehavior khác qua {@link #create(String, VehiclePath, DriverBehavior)}.</p>
 */
public final class VehicleFactory {

    private VehicleFactory() {}   // utility class

    /**
     * Tạo xe với DriverBehavior mặc định cho loại đó.
     *
     * @param typeKey  khóa loại xe (xem bảng trên)
     * @param path     đường đi trong scene
     * @return phương tiện mới sẵn sàng thêm vào SimulationWorld
     * @throws IllegalArgumentException nếu typeKey không hợp lệ
     */
    public static Vehicle create(String typeKey, VehiclePath path) {
        return create(typeKey, path, defaultBehavior(typeKey));
    }

    /**
     * Tạo xe với DriverBehavior tùy chọn.
     *
     * @param typeKey  khóa loại xe
     * @param path     đường đi
     * @param behavior chiến lược lái
     */
    public static Vehicle create(String typeKey, VehiclePath path, DriverBehavior behavior) {
        String id = IdGenerator.next(typeKey);
        return switch (typeKey.toLowerCase()) {
            case "car"       -> new Car(id, path, behavior);
            case "motorbike" -> new Motorbike(id, path, behavior);
            case "bicycle"   -> new Bicycle(id, path, behavior);
            case "bus"       -> new Bus(id, path, behavior);
            case "truck"     -> new Truck(id, path, behavior);
            case "ambulance" -> new Ambulance(id, path, behavior);
            case "firetruck" -> new FireTruck(id, path, behavior);
            default -> throw new IllegalArgumentException(
                    "Loại xe không hợp lệ: '" + typeKey + "'. " +
                    "Hợp lệ: car, motorbike, bicycle, bus, truck, ambulance, firetruck");
        };
    }

    // ─────────────────────────────────────────────────────────────────

    /** DriverBehavior mặc định phù hợp nhất cho từng loại xe. */
    private static DriverBehavior defaultBehavior(String typeKey) {
        return switch (typeKey.toLowerCase()) {
            case "ambulance", "firetruck" -> new EmergencyDriver();
            default                       -> new NormalDriver();
        };
    }
}
