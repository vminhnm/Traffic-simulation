package core.simulation;

public class SimulationEngine {
    private final SimulationWorld world;
    private boolean running;
    private long lastUpdateTime;

    public SimulationEngine(SimulationWorld world) {
        this.world = world;
    }

    public void start() {
        running = true;
        lastUpdateTime = System.nanoTime();
    }

    public void update(double deltaTime) {
        world.update(deltaTime);
    }

    public void pause() {
        running = false;
    }

    public void resume() {
        running = true;
    }
}