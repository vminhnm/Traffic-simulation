package core.vehicle;

import util.Vector2D;

/**
 * Đánh dấu mọi thực thể có khả năng tự di chuyển.
 * Tách khỏi đối tượng tĩnh như TrafficLight hay Road.
 */
public interface Movable {

    /** Di chuyển theo vectơ vận tốc hiện tại trong khoảng thời gian {@code deltaTime} (giây). */
    void move(double deltaTime);

    /** Vận tốc hiện tại (px/s, vector hướng + độ lớn). */
    Vector2D getVelocity();

    /** Tốc độ vô hướng hiện tại (px/s). */
    double getSpeed();

    /** Tốc độ tối đa cho phép (px/s). */
    double getMaxSpeed();
}
