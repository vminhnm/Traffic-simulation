package util;

/**
 * Bất biến (immutable) - vector 2D dùng cho tọa độ và vận tốc trong thế giới mô phỏng.
 */
public final class Vector2D {

    public static final Vector2D ZERO = new Vector2D(0, 0);

    public final double x;
    public final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D multiply(double scalar) {
        return new Vector2D(x * scalar, y * scalar);
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public double distanceTo(Vector2D other) {
        return subtract(other).length();
    }

    public double dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }

    public Vector2D normalize() {
        double len = length();
        if (len < 1e-9) return ZERO;
        return new Vector2D(x / len, y / len);
    }

    /** Góc so với trục x (radian), dùng để xoay sprite khi vẽ. */
    public double angle() {
        return Math.atan2(y, x);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }
}
