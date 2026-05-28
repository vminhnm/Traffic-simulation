package core.driver;

/**
 * Kết quả quyết định lái xe do {@link DriverBehavior} trả về.
 * Vehicle.update() đọc quyết định này và thực thi — Vehicle không cần
 * biết quyết định được ra như thế nào.
 *
 * <p>Dùng factory methods để tạo, tránh nhầm lẫn tham số:</p>
 * <pre>
 *   DrivingDecision.accelerate(80)   // tăng tốc đến 80 px/s
 *   DrivingDecision.stop()           // dừng hẳn
 *   DrivingDecision.yield()          // nhường đường, chậm lại
 * </pre>
 */
public final class DrivingDecision {

    private final DrivingAction action;
    /** Tốc độ mục tiêu (px/s). Bằng 0 nếu action = STOP / YIELD. */
    private final double targetSpeed;

    private DrivingDecision(DrivingAction action, double targetSpeed) {
        this.action      = action;
        this.targetSpeed = targetSpeed;
    }

    // ── Factory methods ──────────────────────────────────────────────

    public static DrivingDecision accelerate(double targetSpeed) {
        return new DrivingDecision(DrivingAction.ACCELERATE, targetSpeed);
    }

    public static DrivingDecision brake(double targetSpeed) {
        return new DrivingDecision(DrivingAction.BRAKE, Math.max(0, targetSpeed));
    }

    public static DrivingDecision stop() {
        return new DrivingDecision(DrivingAction.STOP, 0);
    }

    public static DrivingDecision yield() {
        return new DrivingDecision(DrivingAction.YIELD, 0);
    }

    public static DrivingDecision emergencyPass(double targetSpeed) {
        return new DrivingDecision(DrivingAction.EMERGENCY_PASS, targetSpeed);
    }

    public static DrivingDecision changeLaneLeft(double targetSpeed) {
        return new DrivingDecision(DrivingAction.CHANGE_LANE_LEFT, targetSpeed);
    }

    public static DrivingDecision changeLaneRight(double targetSpeed) {
        return new DrivingDecision(DrivingAction.CHANGE_LANE_RIGHT, targetSpeed);
    }

    public static DrivingDecision mergeBack() {
        return new DrivingDecision(DrivingAction.MERGE_BACK, 0);
    }

    // ── Getters ─────────────────────────────────────────────────────

    public DrivingAction getAction()    { return action;      }
    public double        getTargetSpeed() { return targetSpeed; }

    @Override
    public String toString() {
        return "Decision[" + action + ", speed=" + targetSpeed + "]";
    }
}
