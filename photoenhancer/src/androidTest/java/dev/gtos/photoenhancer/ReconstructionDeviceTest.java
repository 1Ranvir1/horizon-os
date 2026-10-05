package dev.gtos.photoenhancer;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReconstructionDeviceTest {
    @Test public void fullSensorSizeFitsPhoneMemory() throws Exception {
        Bitmap original = Bitmap.createBitmap(3968, 2976, Bitmap.Config.ARGB_8888);
        original.eraseColor(0xff607080);
        long began = android.os.SystemClock.elapsedRealtime();
        Bitmap result = Reconstruction.enhance(InstrumentationRegistry.getInstrumentation().getTargetContext(), original);
        assertEquals(3968, result.getWidth()); assertEquals(2976, result.getHeight());
        android.util.Log.i("GTPhotoEnhanceTest", "Full-sensor synthetic inference: " + (android.os.SystemClock.elapsedRealtime() - began) + "ms");
        result.recycle(); original.recycle();
    }
    @Test public void realModelRunsAcrossTileBoundariesWithoutLargeChanges() throws Exception {
        int w = 710, h = 503;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int v = 30 + (x * 160 / w) + ((x / 30 + y / 30) % 2) * 35;
            pixels[y * w + x] = 0xff000000 | (v << 16) | (v << 8) | v;
        }
        Bitmap original = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        long began = android.os.SystemClock.elapsedRealtime();
        Bitmap result = Reconstruction.enhance(InstrumentationRegistry.getInstrumentation().getTargetContext(), original);
        assertEquals(w, result.getWidth()); assertEquals(h, result.getHeight());
        int[] output = new int[pixels.length]; result.getPixels(output, 0, w, 0, 0, w, h);
        for (int i = 0; i < pixels.length; i++) {
            assertTrue("Pixel change exceeds conservative cap", Math.abs((output[i] & 255) - (pixels[i] & 255)) <= 8);
        }
        android.util.Log.i("GTPhotoEnhanceTest", "Model synthetic inference: " + (android.os.SystemClock.elapsedRealtime() - began) + "ms");
        result.recycle(); original.recycle();
    }
}
