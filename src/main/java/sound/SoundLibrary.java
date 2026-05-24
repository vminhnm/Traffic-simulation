package sound;

import java.util.HashMap;
import java.util.Map;

public final class SoundLibrary {
    private static final Map<SoundType, String> SOUND_FILES = new HashMap<>();

    static {
        SOUND_FILES.put(SoundType.ENGINE_CAR, "engine_car.wav");
        SOUND_FILES.put(SoundType.ENGINE_MOTORBIKE, "engine_motorbike.wav");
        SOUND_FILES.put(SoundType.ENGINE_BUS, "engine_bus.wav");
        SOUND_FILES.put(SoundType.ENGINE_TRUCK, "engine_truck.wav");
        SOUND_FILES.put(SoundType.BICYCLE_BELL, "bicycle_bell.wav");
        SOUND_FILES.put(SoundType.HORN_SHORT, "horn_short.wav");
        SOUND_FILES.put(SoundType.HORN_LONG, "horn_long.wav");
        SOUND_FILES.put(SoundType.TURN_SIGNAL, "turn_signal.wav");
        SOUND_FILES.put(SoundType.AMBULANCE_SIREN, "ambulance_siren.wav");
        SOUND_FILES.put(SoundType.FIRE_TRUCK_SIREN, "fire_truck_siren.wav");
    }

    public static String getFilePath(SoundType soundType) {
        return SOUND_FILES.getOrDefault(soundType, null);
    }

    private SoundLibrary() {}
}
