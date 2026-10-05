package dev.gtos.photoenhancer;
import org.junit.Test;
import static org.junit.Assert.*;
public class RestorationMathTest {
    @Test public void extremePredictionsCannotRewritePixels() {
        for (int value = 0; value < 256; value++) {
            int rgb = 0xff000000 | value * 0x010101;
            for (float prediction : new float[]{-10000, 0, 128, 255, 10000}) {
                int result = RestorationMath.blend(rgb, prediction, value, 100);
                assertTrue(Math.abs((result & 255) - value) <= 8);
                assertEquals(result & 255, (result >> 8) & 255);
                assertEquals(result & 255, (result >> 16) & 255);
            }
        }
    }
    @Test public void invalidInferencePreservesOriginal() {
        assertEquals(0xff2468ab, RestorationMath.blend(0xff2468ab, Float.NaN, 80, 1));
    }
    @Test public void reflectionHandlesBothEdgesAndSinglePixel() {
        assertEquals(1, RestorationMath.reflect(-1, 5));
        assertEquals(3, RestorationMath.reflect(5, 5));
        assertEquals(0, RestorationMath.reflect(-20, 1));
        for (int p = -100; p < 100; p++) assertTrue(RestorationMath.reflect(p, 5) >= 0 && RestorationMath.reflect(p, 5) < 5);
    }
}
