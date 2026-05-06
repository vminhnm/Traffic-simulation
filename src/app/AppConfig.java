package app;

import graphics.renderer.RenderMode;

public class AppConfig {
    private int initialVehicleCount;
    private double trafficDensity;
    private RenderMode renderMode;

    public static AppConfig defaultConfig() {
        AppConfig config = new AppConfig();

        return config;
    }
}