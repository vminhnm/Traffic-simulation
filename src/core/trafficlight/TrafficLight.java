package core.trafficlight;

public abstract class TrafficLight {
    protected String id;
    protected LightColor currentColor;
    protected double remainingTime;
    protected LightTiming timing;

    public abstract boolean shouldShowCountdown();

    public void update(double deltaTime) {
        remainingTime -= deltaTime;
        if (remainingTime <= 0) {
            switchToNextColor();
        }
    }

    public void switchManually() {
        switchToNextColor();
    }

    protected void switchToNextColor() {}
}