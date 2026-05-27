package sound;

<<<<<<< HEAD
import java.util.EnumMap;
import java.util.Map;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

public class SoundManager {

    private static final Map<SoundType, String> PATHS = new EnumMap<>(SoundType.class);
    private static final Map<SoundType, MediaPlayer> PLAYERS = new EnumMap<>(SoundType.class);

    private static boolean muted = false;
    private static double masterVolume = 0.7;

    static {
        PATHS.put(SoundType.TRAFFIC_AMBIENCE,   "/assets/sounds/traffic_ambience.wav");
        PATHS.put(SoundType.BICYCLE_BELL,       "/assets/sounds/bicycle_bell.wav");
        PATHS.put(SoundType.TURN_SIGNAL,        "/assets/sounds/turn_signal.wav");
        PATHS.put(SoundType.HORN_SHORT,         "/assets/sounds/horn_short.wav");
        PATHS.put(SoundType.HORN_LONG,          "/assets/sounds/horn_long.wav");
        PATHS.put(SoundType.AMBULANCE_SIREN,    "/assets/sounds/ambulance_siren.wav");
        PATHS.put(SoundType.FIRE_TRUCK_SIREN,   "/assets/sounds/fire_truck_siren.wav");
        PATHS.put(SoundType.CRASH,              "/assets/sounds/crash.wav");
    }

    // Không cho tạo instance
    private SoundManager() {}

    public static void preloadAll() {
        for (SoundType type : PATHS.keySet()) {
            getOrCreate(type);
        }
    }

    private static MediaPlayer getOrCreate(SoundType type) {
        return PLAYERS.computeIfAbsent(type, t -> {
            String path = PATHS.get(t);
            if (path == null) return null;
            var url = SoundManager.class.getResource(path);
            if (url == null) {
                System.err.println("[SoundManager] Không tìm thấy: " + path);
                return null;
            }
            MediaPlayer player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setVolume(masterVolume);
            System.out.println("[SoundManager] Loaded OK: " + path);
            return player;
        });
    }

    // ── Phát 1 lần (còi, crash, chuông) ─────────────────────────
    public static void play(SoundType type) {
        if (muted) return;
        MediaPlayer player = getOrCreate(type);
        if (player == null) return;
        player.stop();
        player.play();
    }

    // ── Phát loop liên tục (ambience, siren) ─────────────────────
    public static void loop(SoundType type) {
        if (muted) return;
        MediaPlayer player = getOrCreate(type);
        if (player == null) return;
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.play();
    }

    // ── Dừng 1 loại âm thanh ─────────────────────────────────────
    public static void stop(SoundType type) {
        MediaPlayer player = PLAYERS.get(type);
        if (player != null) player.stop();
    }

    // ── Dừng tất cả ──────────────────────────────────────────────
    public static void stopAll() {
        PLAYERS.values().forEach(p -> { if (p != null) p.stop(); });
    }

    // ── Âm lượng ─────────────────────────────────────────────────
    public static void setVolume(SoundType type, double volume) {
        MediaPlayer player = PLAYERS.get(type);
        if (player != null) player.setVolume(volume);
    }

    public static void setMasterVolume(double volume) {
        masterVolume = Math.max(0, Math.min(1, volume));
        PLAYERS.values().forEach(p -> { if (p != null) p.setVolume(masterVolume); });
    }

    public static void setMuted(boolean m) {
        muted = m;
        if (muted) {
            pauseAll();
        } else {
            loop(SoundType.TRAFFIC_AMBIENCE); // ← tự play lại ambience khi unmute
        }
    }

    public static void pauseAll() {
        PLAYERS.values().forEach(p -> { if (p != null) p.pause(); });
    }

    public static void resumeAll() {
        if (muted) return;
        PLAYERS.values().forEach(p -> { if (p != null) p.play(); });
    }

    public static boolean isMuted()        { return muted; }
    public static double getMasterVolume() { return masterVolume; }
}
=======
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private static SoundManager instance;
    private final Map<SoundType, SoundPlayer> soundPlayers = new HashMap<>();
    private final String audioDirectory = "assets/audio/";
    private boolean initialized = false;

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) return;

        for (SoundType soundType : SoundType.values()) {
            String fileName = SoundLibrary.getFilePath(soundType);
            if (fileName != null) {
                String filePath = audioDirectory + fileName;
                try {
                    SoundPlayer player = new SoundPlayer(filePath);
                    soundPlayers.put(soundType, player);
                } catch (Exception e) {
                    System.err.println("Failed to load audio file: " + filePath);
                }
            }
        }
        initialized = true;
    }

    public synchronized void play(SoundType soundType) {
        if (!initialized) initialize();

        SoundPlayer player = soundPlayers.get(soundType);
        if (player != null) {
            try {
                player.play();
            } catch (Exception e) {
                System.err.println("Error playing sound: " + soundType + " - " + e.getMessage());
            }
        }
    }

    public synchronized void loop(SoundType soundType) {
        if (!initialized) initialize();

        SoundPlayer player = soundPlayers.get(soundType);
        if (player != null) {
            try {
                player.loop();
            } catch (Exception e) {
                System.err.println("Error looping sound: " + soundType + " - " + e.getMessage());
            }
        }
    }

    public synchronized void stop(SoundType soundType) {
        SoundPlayer player = soundPlayers.get(soundType);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception e) {
                System.err.println("Error stopping sound: " + soundType + " - " + e.getMessage());
            }
        }
    }

    public synchronized void stopAll() {
        for (SoundPlayer player : soundPlayers.values()) {
            try {
                player.stop();
            } catch (Exception e) {
                System.err.println("Error stopping sound: " + e.getMessage());
            }
        }
    }

    public synchronized void shutdown() {
        stopAll();
        for (SoundPlayer player : soundPlayers.values()) {
            try {
                player.close();
            } catch (Exception e) {
                System.err.println("Error closing sound player: " + e.getMessage());
            }
        }
        soundPlayers.clear();
        initialized = false;
    }
}
>>>>>>> cf0ac8fdd70ff04fb3db369e99db31f25131fda3
