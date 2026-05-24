package sound;

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
