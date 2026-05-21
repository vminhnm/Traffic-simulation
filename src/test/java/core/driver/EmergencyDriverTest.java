package core.driver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class EmergencyDriverTest {

    @Test
    void testEmergencyDriverCreation() {
        EmergencyDriver driver = new EmergencyDriver();
        assertNotNull(driver);
    }

    @Test
    void testDriverStyleName() {
        EmergencyDriver driver = new EmergencyDriver();
        assertNotNull(driver.getStyleName());
    }
}