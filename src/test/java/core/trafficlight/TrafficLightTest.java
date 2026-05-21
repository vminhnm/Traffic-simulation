package core.trafficlight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficLightTest {

    // Tạo một class cụ thể (concrete class) để test các phương thức chung của TrafficLight
    private static class TestTrafficLight extends TrafficLight {
        public TestTrafficLight() {
            this.id = "test-light-id";
        }
        @Override
        public boolean shouldShowCountdown() { return false; }
    }

    @Test
    void shouldChangeColor() {
        TrafficLight light = new TestTrafficLight();

        light.setColor(LightColor.GREEN);

        assertEquals(LightColor.GREEN, light.getColor());
    }

    @Test
    void shouldHaveId() {
        TrafficLight light = new TestTrafficLight();

        assertNotNull(light.getId());
    }
}