package dev.gtos.photoenhancer;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdvancedRestorationDeviceTest {
    @Test public void lowResolutionGets1080MinimumWithoutCropping() throws Exception {
        Bitmap source = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888); source.eraseColor(0xff607080);
        long began = android.os.SystemClock.elapsedRealtime();
        Bitmap output = AdvancedRestoration.enhance(InstrumentationRegistry.getInstrumentation().getTargetContext(), source);
        assertEquals(1920, output.getWidth()); assertEquals(1280, output.getHeight());
        assertTrue(Math.min(output.getWidth(), output.getHeight()) >= 1080);
        for (int border : new int[]{736, 1472}) {
            int a = output.getPixel(border - 1, 500), b = output.getPixel(border, 500);
            for (int shift : new int[]{0, 8, 16}) assertTrue("Tile seam on uniform image", Math.abs(((a >> shift) & 255) - ((b >> shift) & 255)) <= 2);
        }
        android.util.Log.i("GTPhotoEnhanceTest", "Advanced synthetic restoration: " + (android.os.SystemClock.elapsedRealtime() - began) + "ms");
        output.recycle(); source.recycle();
    }
}
