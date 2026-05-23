package core.simulation;

import core.rule.TrafficRuleEvaluator;
import core.vehicle.Vehicle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import util.Vector2D;

public final class CollisionManager {

    private final TrafficRuleEvaluator rules;
    private final double spawnCooldownSeconds;
    private final Map<String, Double> cooldownByVehicleId = new HashMap<>();

    public CollisionManager(double spawnCooldownSeconds) {
        this(new TrafficRuleEvaluator(), spawnCooldownSeconds);
    }

    CollisionManager(TrafficRuleEvaluator rules, double spawnCooldownSeconds) {
        this.rules = rules;
        this.spawnCooldownSeconds = spawnCooldownSeconds;
    }

    public void clear() {
        cooldownByVehicleId.clear();
    }

    public void startSpawnCooldown(Vehicle vehicle) {
        cooldownByVehicleId.put(vehicle.getId(), spawnCooldownSeconds);
    }

    public boolean isInCooldown(Vehicle vehicle) {
        return cooldownByVehicleId.containsKey(vehicle.getId());
    }

    public void updateCooldowns(double deltaTime) {
        cooldownByVehicleId.replaceAll((id, remaining) -> remaining - deltaTime);
        cooldownByVehicleId.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public List<CollisionEvent> detectAndResolve(SimulationWorld world) {
        List<CollisionEvent> events = new ArrayList<>();
        List<Vehicle> vehicles = new ArrayList<>(world.getVehicles());

        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle first = vehicles.get(i);
            if (shouldSkip(first)) continue;

            for (int j = i + 1; j < vehicles.size(); j++) {
                Vehicle second = vehicles.get(j);
                if (shouldSkip(second)) continue;

                if (rules.isColliding(first, second)) {
                    events.add(resolve(first, second));
                }
            }
        }

        return events;
    }

    private boolean shouldSkip(Vehicle vehicle) {
        return vehicle.isCrashed()
                || vehicle.isFinished()
                || isInCooldown(vehicle);
    }

    private CollisionEvent resolve(Vehicle first, Vehicle second) {
        CollisionEvent.Type type;

        if (first.isPriorityVehicle() && second.isPriorityVehicle()) {
            type = CollisionEvent.Type.PRIORITY_YIELD;
        } else if (first.isPriorityVehicle()) {
            second.setCrashed();
            type = CollisionEvent.Type.PRIORITY_PUSH;
        } else if (second.isPriorityVehicle()) {
            first.setCrashed();
            type = CollisionEvent.Type.PRIORITY_PUSH;
        } else {
            first.setCrashed();
            second.setCrashed();
            type = CollisionEvent.Type.NORMAL_CRASH;
        }

        return new CollisionEvent(first, second, type, midpoint(first, second));
    }

    private Vector2D midpoint(Vehicle first, Vehicle second) {
        return new Vector2D(
                (first.getPosition().x + second.getPosition().x) / 2.0,
                (first.getPosition().y + second.getPosition().y) / 2.0);
    }
}
