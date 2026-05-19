package core.driver;

/**
 * Các hành động lái xe có thể xảy ra.
 * DrivingDecision đóng gói hành động + tham số bổ sung.
 */
public enum DrivingAction {
    ACCELERATE,
    BRAKE,
    STOP,
    YIELD,            // nhường đường
    EMERGENCY_PASS    // vượt khẩn cấp (xe ưu tiên)
}
