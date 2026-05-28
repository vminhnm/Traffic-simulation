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
    EMERGENCY_PASS,   // vượt khẩn cấp (xe ưu tiên)
    CHANGE_LANE_LEFT, // vượt / chuyển làn trái
    CHANGE_LANE_RIGHT, // vượt / chuyển làn phải / dạt vào lề
    MERGE_BACK        // drift lateralOffset back to 0 while staying stopped
}
