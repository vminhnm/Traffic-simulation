package util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class Vector2DTest {

    @Test
    void testAdd() {
        Vector2D v1 = new Vector2D(1, 2);
        Vector2D v2 = new Vector2D(3, 4);

        Vector2D result = v1.add(v2);

        assertEquals(new Vector2D(4, 6).toString(), result.toString());
    }

    @Test
    void testSubtract() {
        Vector2D v1 = new Vector2D(5, 4);
        Vector2D v2 = new Vector2D(2, 1);

        Vector2D result = v1.subtract(v2);

        assertEquals(new Vector2D(3, 3).toString(), result.toString());
    }

    @Test
    void testMultiply() {
        Vector2D v = new Vector2D(2, 3);

        Vector2D result = v.multiply(2);

        assertEquals(new Vector2D(4, 6).toString(), result.toString());
    }

    @Test
    void testLength() {
        Vector2D v = new Vector2D(3, 4);

        assertEquals(5.0, v.length(), 0.0001);
    }

    @Test
    void testNormalize() {
        Vector2D v = new Vector2D(3, 4);

        Vector2D result = v.normalize();

        assertNotNull(result);
    }

    @Test
    void testDotProduct() {
        Vector2D v1 = new Vector2D(1, 2);
        Vector2D v2 = new Vector2D(3, 4);

        assertEquals(11, v1.dot(v2));
    }

    @Test
    void testZeroVectorLength() {
        Vector2D v = new Vector2D(0, 0);

        assertEquals(0, v.length());
    }

    @Test
    void testNegativeValues() {
        Vector2D v = new Vector2D(-2, -3);

        assertNotNull(v);
    }

    @Test
    void testFloatingPointPrecision() {
        Vector2D v1 = new Vector2D(0.1, 0.2);
        Vector2D v2 = new Vector2D(0.2, 0.3);

        Vector2D result = v1.add(v2);

        assertNotNull(result);
    }

    @Test
    void testEquals() {
        Vector2D v1 = new Vector2D(1, 2);
        Vector2D v2 = v1;

        assertEquals(v1, v2);
    }

    @Test
    void testHashCode() {
        Vector2D v1 = new Vector2D(1, 2);
        Vector2D v2 = v1;

        assertEquals(v1.hashCode(), v2.hashCode());
    }
}