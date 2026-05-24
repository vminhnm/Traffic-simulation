package core.driver;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class NormalDriverTest {

    @Test
    void testNormalDriverCreation() {
        NormalDriver driver = new NormalDriver();
        assertNotNull(driver);
    }

    @Test
    void testDriverStyleName() {
        NormalDriver driver = new NormalDriver();
        assertNotNull(driver.getStyleName());
    }
}