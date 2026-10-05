package dev.gtos.photoenhancer;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdvancedRestorationMathTest {
    @Test public void strongerModelStillCannotReplaceLargeColourDifferences() {
        for (int source = 0; source < 256; source++) for (float predicted : new float[]{-500, 0, 128, 255, 500}) {
            int result = AdvancedRestoration.reconstruct(source, predicted);
            assertTrue(result >= 0 && result <= 255);
            assertTrue(Math.abs(result - source) <= 40);
        }
    }
    @Test public void invalidInferenceKeepsSource() {
        assertEquals(98, AdvancedRestoration.reconstruct(98, Float.NaN));
        assertEquals(98, AdvancedRestoration.reconstruct(98, Float.POSITIVE_INFINITY));
    }
}
