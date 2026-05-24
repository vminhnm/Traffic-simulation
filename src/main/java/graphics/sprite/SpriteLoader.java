package graphics.sprite;

import java.util.EnumMap;
import java.util.Map;

import javafx.scene.image.Image;

public class SpriteLoader {

    private static final Map<RenderAssetKey, String> PATHS = new EnumMap<>(RenderAssetKey.class);
    private static final Map<RenderAssetKey, Image>  CACHE = new EnumMap<>(RenderAssetKey.class);

   static {
    // ── Ambulance ─────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.AMBULANCE_EAST,      "/assets/sprites/ambulance_EAST.png");
    PATHS.put(RenderAssetKey.AMBULANCE_NORTH,     "/assets/sprites/ambulance_NORTH.png");
    PATHS.put(RenderAssetKey.AMBULANCE_NORTHEAST, "/assets/sprites/ambulance_NORTHEAST.png");
    PATHS.put(RenderAssetKey.AMBULANCE_NORTHWEST, "/assets/sprites/ambulance_NORTHWEST.png");
    PATHS.put(RenderAssetKey.AMBULANCE_SOUTH,     "/assets/sprites/ambulance_SOUTH.png");
    PATHS.put(RenderAssetKey.AMBULANCE_SOUTHEAST, "/assets/sprites/ambulance_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.AMBULANCE_SOUTHWEST, "/assets/sprites/ambulance_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.AMBULANCE_WEST,      "/assets/sprites/ambulance_WEST.png");

    // ── Bicycle ───────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.BICYCLE_EAST,        "/assets/sprites/bicycle_EAST.png");
    PATHS.put(RenderAssetKey.BICYCLE_NORTH,       "/assets/sprites/bicycle_NORTH.png");
    PATHS.put(RenderAssetKey.BICYCLE_NORTHEAST,   "/assets/sprites/bicycle_NORTHEAST.png");
    PATHS.put(RenderAssetKey.BICYCLE_NORTHWEST,   "/assets/sprites/bicycle_NORTHWEST.png");
    PATHS.put(RenderAssetKey.BICYCLE_SOUTH,       "/assets/sprites/bicycle_SOUTH.png");
    PATHS.put(RenderAssetKey.BICYCLE_SOUTHEAST,   "/assets/sprites/bicycle_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.BICYCLE_SOUTHWEST,   "/assets/sprites/bicycle_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.BICYCLE_WEST,        "/assets/sprites/bicycle_WEST.png");

    // ── Bus ───────────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.BUS_EAST,            "/assets/sprites/bus_EAST.png");
    PATHS.put(RenderAssetKey.BUS_NORTH,           "/assets/sprites/bus_NORTH.png");
    PATHS.put(RenderAssetKey.BUS_NORTHEAST,       "/assets/sprites/bus_NORTHEAST.png");
    PATHS.put(RenderAssetKey.BUS_NORTHWEST,       "/assets/sprites/bus_NORTHWEST.png");
    PATHS.put(RenderAssetKey.BUS_SOUTH,           "/assets/sprites/bus_SOUTH.png");
    PATHS.put(RenderAssetKey.BUS_SOUTHEAST,       "/assets/sprites/bus_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.BUS_SOUTHWEST,       "/assets/sprites/bus_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.BUS_WEST,            "/assets/sprites/bus_WEST.png");

    // ── Car ───────────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.CAR_EAST,            "/assets/sprites/car_EAST.png");
    PATHS.put(RenderAssetKey.CAR_NORTH,           "/assets/sprites/car_NORTH.png");
    PATHS.put(RenderAssetKey.CAR_NORTHEAST,       "/assets/sprites/car_NORTHEAST.png");
    PATHS.put(RenderAssetKey.CAR_NORTHWEST,       "/assets/sprites/car_NORTHWEST.png");
    PATHS.put(RenderAssetKey.CAR_SOUTH,           "/assets/sprites/car_SOUTH.png");
    PATHS.put(RenderAssetKey.CAR_SOUTHEAST,       "/assets/sprites/car_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.CAR_SOUTHWEST,       "/assets/sprites/car_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.CAR_WEST,            "/assets/sprites/car_WEST.png");

    // ── FireTruck ─────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.FIRETRUCK_EAST,      "/assets/sprites/firetruck_EAST.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_NORTH,     "/assets/sprites/firetruck_NORTH.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_NORTHEAST, "/assets/sprites/firetruck_NORTHEAST.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_NORTHWEST, "/assets/sprites/firetruck_NORTHWEST.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_SOUTH,     "/assets/sprites/firetruck_SOUTH.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_SOUTHEAST, "/assets/sprites/firetruck_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_SOUTHWEST, "/assets/sprites/firetruck_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.FIRETRUCK_WEST,      "/assets/sprites/firetruck_WEST.png");

    // ── Motorbike ─────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.MOTORBIKE_EAST,      "/assets/sprites/motorbike_EAST.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_NORTH,     "/assets/sprites/motorbike_NORTH.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_NORTHEAST, "/assets/sprites/motorbike_NORTHEAST.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_NORTHWEST, "/assets/sprites/motorbike_NORTHWEST.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_SOUTH,     "/assets/sprites/motorbike_SOUTH.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_SOUTHEAST, "/assets/sprites/motorbike_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_SOUTHWEST, "/assets/sprites/motorbike_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.MOTORBIKE_WEST,      "/assets/sprites/motorbike_WEST.png");

    // ── Truck ─────────────────────────────────────────────────────
    PATHS.put(RenderAssetKey.TRUCK_EAST,          "/assets/sprites/truck_EAST.png");
    PATHS.put(RenderAssetKey.TRUCK_NORTH,         "/assets/sprites/truck_NORTH.png");
    PATHS.put(RenderAssetKey.TRUCK_NORTHEAST,     "/assets/sprites/truck_NORTHEAST.png");
    PATHS.put(RenderAssetKey.TRUCK_NORTHWEST,     "/assets/sprites/truck_NORTHWEST.png");
    PATHS.put(RenderAssetKey.TRUCK_SOUTH,         "/assets/sprites/truck_SOUTH.png");
    PATHS.put(RenderAssetKey.TRUCK_SOUTHEAST,     "/assets/sprites/truck_SOUTHEAST.png");
    PATHS.put(RenderAssetKey.TRUCK_SOUTHWEST,     "/assets/sprites/truck_SOUTHWEST.png");
    PATHS.put(RenderAssetKey.TRUCK_WEST,          "/assets/sprites/truck_WEST.png");
}
    // Không cho tạo instance
    private SpriteLoader() {}

    public static Image get(RenderAssetKey key) {
        if (key == null) return null;
        return CACHE.computeIfAbsent(key, k -> {
            String path = PATHS.get(k);
            if (path == null) return null;
            var stream = SpriteLoader.class.getResourceAsStream(path);
            if (stream == null) {
                System.err.println("[SpriteLoader] Không tìm thấy: " + path);
                return null;
            }
            System.out.println("[SpriteLoader] Loaded OK: " + path); // ← thêm dòng này
            return new Image(stream);
        });
    }

    /** Gọi 1 lần khi khởi động app để load trước tất cả ảnh */
    public static void preloadAll() {
        for (RenderAssetKey key : PATHS.keySet()) {
            get(key);
        }
    }
}
