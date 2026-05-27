package core.vehicle;

import java.util.List;

import core.driver.DriverBehavior;
import core.driver.DrivingDecision;
import core.road.VehiclePath;
import core.simulation.SimulationWorld;
import util.Vector2D;

/**
 * <b>Lớp trừu tượng cho mọi phương tiện giao thông.</b>
 *
 * <h2>Trách nhiệm</h2>
 * <ul>
 *   <li>Lưu trạng thái vật lý: vị trí, vận tốc, kích thước.</li>
 *   <li>Bám theo {@link VehiclePath} (danh sách waypoint).</li>
 *   <li>Ủy thác quyết định lái cho {@link DriverBehavior} (Strategy pattern).</li>
 *   <li>Cung cấp {@link RenderableState} cho Renderer — <em>không tự vẽ</em>.</li>
 * </ul>
 *
 * <h2>Mở rộng</h2>
 * Để thêm loại xe mới, chỉ cần kế thừa {@code Vehicle} (hoặc
 * {@link PriorityVehicle}) và ghi đè {@link #buildProfile()} để
 * trả về {@link VehicleProfile} phù hợp. <b>Không sửa TrafficController.</b>
 *
 * <h2>Tách biệt Logic ↔ Vẽ</h2>
 * Toàn bộ dữ liệu hiển thị được đóng gói trong {@link RenderableState}.
 * Renderer chỉ nhận snapshot này — không truy cập trực tiếp Vehicle.
 */
public abstract class Vehicle implements Movable {

    // ── Định danh ────────────────────────────────────────────────────
    protected final String id;

    // ── Vật lý ───────────────────────────────────────────────────────
    /** Vị trí tâm xe trong hệ tọa độ thế giới (px). */
    protected Vector2D position;
    /** Vận tốc hiện tại: hướng + độ lớn (px/s). */
    protected Vector2D velocity;
    /** Góc xoay (radian) — dùng để render sprite đúng hướng. */
    protected double   rotation;

    protected double maxSpeed;       // px/s
    protected double acceleration;   // px/s²
    protected double currentSpeed;   // px/s (vô hướng)

    // ── Kích thước ───────────────────────────────────────────────────
    protected double length;
    protected double width;

    // ── Đường đi ─────────────────────────────────────────────────────
    /** Đường đi được gán khi xe vào scene. */
    protected VehiclePath path;
    /** Chỉ số waypoint hiện tại (xe đang tiến đến waypoint[waypointIndex]). */
    protected int waypointIndex;
    /** Xe đã tới điểm cuối đường, sẵn sàng bị xóa khỏi scene. */
    protected boolean finished;

    // ── Chiến lược lái ───────────────────────────────────────────────
    /** Bộ não lái — có thể hoán đổi tại runtime. */
    protected DriverBehavior driverBehavior;

    // ── Trạng thái phụ ───────────────────────────────────────────────
    protected boolean stopped;
    protected boolean yielding;
    protected boolean crashed;
    /** Bộ đếm thời gian nháy đèn (dùng cho xe ưu tiên). */
    protected double  flashTimer;
    protected boolean flashState;
    /** Offset ngang khi vượt xe (px, dương = lệch trái). */
    protected double  lateralOffset;

    // ── Cấu hình loại xe ─────────────────────────────────────────────
    private final VehicleProfile profile;

    // ─────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param id             định danh duy nhất (ví dụ "car-7")
     * @param path           đường đi trong scene
     * @param driverBehavior chiến lược lái (NormalDriver / AggressiveDriver…)
     */
    protected Vehicle(String id, VehiclePath path, DriverBehavior driverBehavior) {
        this.id             = id;
        this.path           = path;
        this.driverBehavior = driverBehavior;
        this.profile        = buildProfile();

        // Áp giá trị mặc định từ profile
        this.maxSpeed     = profile.getDefaultMaxSpeed();
        this.acceleration = profile.getDefaultAcceleration();
        this.length       = profile.getDefaultLength();
        this.width        = profile.getDefaultWidth();

        // Khởi tạo vị trí tại điểm bắt đầu đường đi
        this.position      = path.getStartPosition();
        this.waypointIndex = 1;   // bắt đầu tiến đến waypoint thứ 2
        this.velocity      = Vector2D.ZERO;
        this.currentSpeed  = 0;
        this.rotation      = computeRotationToNext();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Template Method — subclass chỉ cần ghi đè buildProfile()
    // ─────────────────────────────────────────────────────────────────

    /**
     * Cung cấp cấu hình bất biến cho loại xe này.
     * Được gọi một lần duy nhất trong constructor.
     */
    protected abstract VehicleProfile buildProfile();

    // ─────────────────────────────────────────────────────────────────
    //  Vòng lặp cập nhật chính
    // ─────────────────────────────────────────────────────────────────

    /**
     * Được SimulationEngine gọi mỗi frame.
     * <ol>
     *   <li>Hỏi DriverBehavior để lấy quyết định.</li>
     *   <li>Áp quyết định vào vận tốc / trạng thái.</li>
     *   <li>Di chuyển theo path (waypoints).</li>
     *   <li>Cập nhật hiệu ứng phụ (đèn nháy…).</li>
     * </ol>
     */
    public final void update(double deltaTime, SimulationWorld world) {
        if (finished || crashed) return;

        // 1. Lấy quyết định từ bộ não lái
        DrivingDecision decision = driverBehavior.decide(this, world);

        // 2. Áp dụng quyết định
        applyDecision(decision, deltaTime);

        // 3. Bám theo path
        if (!stopped) {
            moveAlongPath(deltaTime);
        }

        // 4. Cập nhật hiệu ứng phụ
        updateEffects(deltaTime);
    }

    /**
     * Áp quyết định lái vào trạng thái vật lý.
     * Lớp con có thể ghi đè để thêm hành vi đặc biệt (ví dụ
     * EmergencyDriver bỏ qua giới hạn tốc độ).
     */
    private double stoppedTimer = 0;
    private static final double HORN_DELAY     = 5.0; // giây dừng trước khi bấm còi
    /** Thời gian dừng tối đa trước khi tự động nhích để phá deadlock (giây). */
    private static final double DEADLOCK_TIMEOUT = 3.0;
    /** Cờ đánh dấu xe đang ở chế độ "phá deadlock" — nhích từ từ về phía trước. */
    private boolean deadlockEscape = false;

    protected void applyDecision(DrivingDecision decision, double deltaTime) {
        stopped  = false;
        yielding = false;

        // ── Kiểm tra deadlock timeout ────────────────────────────────
        // Nếu xe nhận lệnh STOP quá lâu mà không phải do đèn đỏ,
        // kích hoạt chế độ thoát deadlock: nhích chậm về phía trước.
        if (decision.getAction() == core.driver.DrivingAction.STOP && deadlockEscape) {
            // Đang thoát deadlock: tăng tốc chậm, bỏ qua lệnh STOP này
            currentSpeed = Math.min(currentSpeed + acceleration * 0.5 * deltaTime, maxSpeed * 0.3);
            stopped = false;
            // Kết thúc chế độ escape sau khi xe đã di chuyển đủ xa
            if (currentSpeed > maxSpeed * 0.2) {
                deadlockEscape = false;
                stoppedTimer   = 0;
            }
            return;
        }

        switch (decision.getAction()) {
            case ACCELERATE, EMERGENCY_PASS -> {
                stoppedTimer   = 0;
                deadlockEscape = false;
                double target = decision.getTargetSpeed();
                currentSpeed = Math.min(currentSpeed + acceleration * deltaTime, target);
                currentSpeed = Math.min(currentSpeed, maxSpeed * speedMultiplier());
                
                // Khi tăng tốc bình thường, xe dần quay trở về tâm làn đường
                if (lateralOffset > 0) lateralOffset = Math.max(0, lateralOffset - 15 * deltaTime);
                else if (lateralOffset < 0) lateralOffset = Math.min(0, lateralOffset + 15 * deltaTime);
            }
            case BRAKE -> {
                stoppedTimer   = 0;
                deadlockEscape = false;
                double target = decision.getTargetSpeed();
                currentSpeed = Math.max(currentSpeed - acceleration * 2 * deltaTime, target);
            }
            case STOP -> {
                currentSpeed = 0;
                stopped      = true;
                stoppedTimer += deltaTime; // ← chỉ đếm khi dừng hẳn
                if (stoppedTimer >= HORN_DELAY) {
                    sound.SoundManager.play(sound.SoundType.HORN_SHORT);
                    stoppedTimer = -999; // reset để không phát liên tục
                }
                // Phá deadlock: nếu dừng quá DEADLOCK_TIMEOUT giây → nhích thoát
                if (stoppedTimer >= DEADLOCK_TIMEOUT && stoppedTimer < HORN_DELAY) {
                    deadlockEscape = true;
                    stopped        = false;
                    currentSpeed   = maxSpeed * 0.1; // nhích chậm
                }
            }
            case YIELD -> {
                stoppedTimer = 0;
                currentSpeed = Math.max(currentSpeed - acceleration * deltaTime, 0);
                yielding     = true;
                // Xe dạt sang lề phải để nhường đường cho xe ưu tiên
                lateralOffset = Math.min(lateralOffset + 20 * deltaTime, 15);
                if (currentSpeed == 0) stopped = true;
            }
            case CHANGE_LANE_LEFT -> {
                double target = decision.getTargetSpeed();
                currentSpeed = Math.min(currentSpeed + acceleration * deltaTime, target);
                lateralOffset = Math.max(lateralOffset - 25 * deltaTime, -50); // Dạt trái
            }
            case CHANGE_LANE_RIGHT -> {
                double target = decision.getTargetSpeed();
                currentSpeed = Math.min(currentSpeed + acceleration * deltaTime, target);
                lateralOffset = Math.min(lateralOffset + 25 * deltaTime, 50); // Dạt phải
            }
        }
    }

    /**
     * Nhân tốc độ tối đa — lớp con override nếu muốn có tốc độ base khác.
     */
    protected double speedMultiplier() { return 1.0; }

    // ─────────────────────────────────────────────────────────────────
    //  Di chuyển theo đường (waypoints)
    // ─────────────────────────────────────────────────────────────────

    /** Di chuyển xe dọc theo path trong khoảng thời gian deltaTime. */
    private void moveAlongPath(double deltaTime) {
        List<Vector2D> waypoints = path.getWaypoints();
        if (waypointIndex >= waypoints.size()) {
            finished = true;
            return;
        }

        Vector2D target    = waypoints.get(waypointIndex);
        Vector2D direction = target.subtract(position).normalize();

        double step = currentSpeed * deltaTime;
        double dist = position.distanceTo(target);

        if (step >= dist) {
            // Đã vượt qua waypoint này, tiến đến cái tiếp theo
            position = target;
            waypointIndex++;
            if (waypointIndex < waypoints.size()) {
                double oldRotation = rotation; // lưu góc cũ
                rotation = computeRotationToNext();
                 // Phát tiếng xi nhan khi rẽ > 20°
                double angleDiff = Math.abs(rotation - oldRotation);
                if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;
                if (angleDiff > Math.toRadians(20)) {
                    sound.SoundManager.play(sound.SoundType.TURN_SIGNAL);
            }
            } else {
                finished = true;
            }
        } else {
            position = position.add(direction.multiply(step));
            rotation = direction.angle();
        }

        velocity = direction.multiply(currentSpeed);
    }

    /** Tính góc xoay dựa trên hướng đến waypoint kế tiếp. */
    private double computeRotationToNext() {
        List<Vector2D> wps = path.getWaypoints();
        if (waypointIndex >= wps.size()) return rotation;
        Vector2D dir = wps.get(waypointIndex).subtract(position).normalize();
        return dir.angle();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Hiệu ứng phụ
    // ─────────────────────────────────────────────────────────────────

    /** Cập nhật hiệu ứng: đèn nháy, xi-nhan… Lớp con ghi đè để thêm. */
    protected void updateEffects(double deltaTime) {
        // Đèn xi-nhan nhấp nháy mặc định khi đang rẽ
        flashTimer += deltaTime;
        if (flashTimer >= 0.5) {
            flashTimer = 0;
            flashState = !flashState;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Movable interface
    // ─────────────────────────────────────────────────────────────────

    @Override public void move(double deltaTime) { moveAlongPath(deltaTime); }
    @Override public Vector2D getVelocity()       { return velocity;     }
    @Override public double   getSpeed()           { return currentSpeed; }
    @Override public double   getMaxSpeed()        { return maxSpeed;     }

    // ─────────────────────────────────────────────────────────────────
    //  Snapshot cho Renderer — KHÔNG để renderer gọi getter khác
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tạo snapshot trạng thái hiển thị cho Renderer.
     * Renderer chỉ được phép gọi method này — mọi logic vẽ
     * đều chạy trên {@link RenderableState}, không trực tiếp trên Vehicle.
     */
    public RenderableState toRenderableState() {
        // Tính toán tọa độ vẽ có áp dụng độ dạt ngang (lateralOffset)
        // Dùng vector vuông góc với hướng di chuyển hiện tại
        Vector2D rightVector = new Vector2D(Math.cos(rotation + Math.PI/2), Math.sin(rotation + Math.PI/2));
        Vector2D renderPos = position.add(rightVector.multiply(lateralOffset));

        return RenderableState.builder(id)
                .position(renderPos)
                .rotation(rotation)
                .length(profile.getDefaultRenderLength())
                .width(profile.getDefaultRenderWidth())
                .physicalLength(profile.getDefaultLength())
                .physicalWidth(profile.getDefaultWidth())
                .basicLabel(profile.getBasicLabel())
                .bodyColor(profile.getBodyColor())
                .roofColor(profile.getRoofColor())
                //.spritePath(profile.getSpritePath())
                .spriteKey(profile.getSpriteKey())
                .isPriority(isPriorityVehicle())
                .sirenFlash(flashState && isPriorityVehicle())
                .isStopped(stopped)
                .isYielding(yielding)
                .isCrashed(crashed)
                .driverStyle(driverBehavior.getStyleName())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    //  API công khai
    // ─────────────────────────────────────────────────────────────────

    /** @return true nếu đây là xe ưu tiên (cứu thương, cứu hỏa…). */
    public abstract boolean isPriorityVehicle();

    /** Hoán đổi bộ não lái tại runtime. */
    public void setDriverBehavior(DriverBehavior behavior) {
        this.driverBehavior = behavior;
    }

    public String         getId()             { return id;             }
    public Vector2D       getPosition()       { return position;       }
    public double         getRotation()       { return rotation;       }
    public double         getLength()         { return length;         }
    public double         getWidth()          { return width;          }
    public VehiclePath    getPath()           { return path;           }
    public int            getWaypointIndex()  { return waypointIndex;  }
    public boolean        isFinished()        { return finished;       }
    public boolean        isStopped()         { return stopped;        }
    public boolean        isYielding()        { return yielding;       }
    public boolean        isCrashed()         { return crashed;        }

    public void setCrashed() {
        this.crashed = true;
        this.currentSpeed = 0;
        this.velocity = Vector2D.ZERO;
    }

    public VehicleProfile getProfile()        { return profile;        }
    public DriverBehavior getDriverBehavior() { return driverBehavior; }
    public double         getLateralOffset()  { return lateralOffset;  }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + " @" + position + "]";
    }
}
