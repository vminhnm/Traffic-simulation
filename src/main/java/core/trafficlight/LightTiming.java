package core.trafficlight;

public class LightTiming {
    private double redDuration;
    private double yellowDuration;
    private double greenDuration;

    public LightTiming(double redDuration, double yellowDuration, double greenDuration) {
        this.redDuration = redDuration;
        this.yellowDuration = yellowDuration;
        this.greenDuration = greenDuration;
    }

    public double getRedDuration() {
        return redDuration;
    }

    public double getYellowDuration() {
        return yellowDuration;
    }

    public double getGreenDuration() {
        return greenDuration;
    }
}