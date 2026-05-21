package util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Vector2DTest {

    @Test
    void testAdd() {

        Vector2D a = new Vector2D(1, 2);
        Vector2D b = new Vector2D(3, 4);

        Vector2D result = a.add(b);

        assertEquals(4, result.x);
        assertEquals(6, result.y);
    }

    @Test
    void testLength() {

        Vector2D v = new Vector2D(3, 4);

        assertEquals(5.0, v.length(), 0.0001);
    }
}