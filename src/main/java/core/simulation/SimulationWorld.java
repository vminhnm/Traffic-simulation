package core.simulation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import core.trafficlight.TrafficLight;
import core.vehicle.Vehicle;

/**
 * Trạng thái toàn cục của mô phỏng.
 * Được truyền vào DriverBehavior.decide() để AI lái xe
 * quan sát thế giới xung quanh (xe khác, đèn, đường…).
 */
public class SimulationWorld {

    private List<Vehicle>                   vehicles = new ArrayList<>();
    private final Map<String, TrafficLight> lightMap = new HashMap<>();

    // ── Phương tiện ─────────────────────────────────────────────────

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public void setVehicles(List<Vehicle> v) { this.vehicles = new ArrayList<>(v); }
    public void addVehicle(Vehicle v)        { vehicles.add(v);    }
    public void removeVehicle(Vehicle v)     { vehicles.remove(v); }

    // ── Đèn giao thông ──────────────────────────────────────────────

    public void registerTrafficLight(TrafficLight light) {
        lightMap.put(light.getId(), light);
    }

    public Optional<TrafficLight> findTrafficLight(String lightId) {
        return Optional.ofNullable(lightMap.get(lightId));
    }

    public Collection<TrafficLight> getAllLights() {
        return Collections.unmodifiableCollection(lightMap.values());
    }

    // ── Cập nhật ────────────────────────────────────────────────────
    public void update(double deltaTime) {
        for (Vehicle v : vehicles) {
            v.update(deltaTime, this);
        }
        for (TrafficLight light : lightMap.values()) {
            light.update(deltaTime);
        }
    }
}
