package sound;

/**
 * Các loại âm thanh trong mô phỏng.
 * SoundManager sẽ dùng enum này để tra cứu và phát file âm thanh tương ứng.
 */
public enum SoundType {
    ENGINE_CAR,
    ENGINE_MOTORBIKE,
    ENGINE_BUS,
    ENGINE_TRUCK,
    BICYCLE_BELL,
    TURN_SIGNAL,          // tiếng xi-nhan
    HORN_SHORT,           // còi ngắn (xin vượt)
    HORN_LONG,            // còi dài
    AMBULANCE_SIREN,
    FIRE_TRUCK_SIREN
}
