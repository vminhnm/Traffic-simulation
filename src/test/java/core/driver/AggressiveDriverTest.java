package core.driver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class AggressiveDriverTest {

    @Test
    void testDriverCreation() {
        AggressiveDriver driver = new AggressiveDriver();
        assertNotNull(driver);
    }

    @Test
    void testDriverStyleName() {
        AggressiveDriver driver = new AggressiveDriver();
        assertNotNull(driver.getStyleName());
    }
}