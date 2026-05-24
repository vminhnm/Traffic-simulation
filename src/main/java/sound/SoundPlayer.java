package sound;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.File;

public class SoundPlayer {
    private Clip clip;
    private boolean isLooping;

    public SoundPlayer(String filePath) throws Exception {
        File soundFile = new File(filePath);
        if (!soundFile.exists()) {
            throw new IllegalArgumentException("Audio file not found: " + filePath);
        }

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
        clip = AudioSystem.getClip();
        clip.open(audioInputStream);
        isLooping = false;
    }

    public synchronized void play() {
        if (clip != null && !clip.isRunning()) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public synchronized void loop() {
        if (clip != null) {
            if (!clip.isRunning()) {
                clip.setFramePosition(0);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                isLooping = true;
            }
        }
    }

    public synchronized void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.setFramePosition(0);
            isLooping = false;
        }
    }

    public synchronized void setVolume(float volume) {
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log(Math.max(volume, 0.0001)) / Math.log(10.0) * 20.0);
            gainControl.setValue(dB);
        }
    }

    public synchronized boolean isRunning() {
        return clip != null && clip.isRunning();
    }

    public synchronized void close() {
        if (clip != null) {
            clip.stop();
            clip.close();
        }
    }
}
