package app;

import graphics.renderer.RenderMode;

public class AppConfig {
    private int initialVehicleCount;
    private double trafficDensity;
    private RenderMode renderMode;

    public static AppConfig defaultConfig() {
        AppConfig config = new AppConfig();
        config.setInitialVehicleCount(50);
        config.setTrafficDensity(0.5);
        return config;
    }

    public int getInitialVehicleCount() {
        return initialVehicleCount;
    }

    public void setInitialVehicleCount(int initialVehicleCount) {
        this.initialVehicleCount = initialVehicleCount;
    }

    public double getTrafficDensity() {
        return trafficDensity;
    }

    public void setTrafficDensity(double trafficDensity) {
        this.trafficDensity = trafficDensity;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(RenderMode renderMode) {
        this.renderMode = renderMode;
    }
}