package dev.gtos.photoenhancer;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class PhotoSaveDeviceTest {
    @Test public void servicePublishesSeparateCopyAndLeavesOriginalBytesIntact() throws Exception { runCase(false); }
    @Test public void reconstructionPublishesLabelled1080CopyAndPreservesOriginal() throws Exception { runCase(true); }
    private void runCase(boolean advanced) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean previousMode = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("advanced", true);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("advanced", advanced).commit();
        ContentResolver resolver = context.getContentResolver();
        String name = "GT_ENHANCE_TEST_" + System.currentTimeMillis();
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri source = null, enhanced = null;
        try {
            ContentValues values = new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/GTEnhanceTest/");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            source = resolver.insert(collection, values); assertNotNull(source);
            Bitmap bitmap = Bitmap.createBitmap(710, 503, Bitmap.Config.ARGB_8888); bitmap.eraseColor(0xff708090);
            try (OutputStream out = resolver.openOutputStream(source)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)); }
            bitmap.recycle();
            ContentValues publish = new ContentValues(); publish.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(source, publish, null, null);
            byte[] before; try (InputStream in = resolver.openInputStream(source)) { before = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()); }
            context.startForegroundService(new Intent(context, EnhanceService.class).setAction(EnhanceService.ACTION).setData(source).putExtra("manual", true));
            long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
            while (enhanced == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT}, MediaStore.Images.Media.DISPLAY_NAME + "=? AND " + MediaStore.Images.Media.IS_PENDING + "=0", new String[]{name + (advanced ? "_AI_reconstructed.jpg" : "_enhanced.jpg")}, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        enhanced = ContentUris.withAppendedId(collection, cursor.getLong(0));
                        assertEquals(advanced ? 2048 : 710, cursor.getInt(1)); assertEquals(advanced ? 1452 : 503, cursor.getInt(2));
                    }
                }
                if (enhanced == null) Thread.sleep(250);
            }
            assertNotNull("No published enhanced photo", enhanced);
            try (InputStream in = resolver.openInputStream(source)) { assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())); }
            assertNotEquals(source, enhanced);
        } finally {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("advanced", previousMode).commit();
            // Delete only this test's newly created synthetic files, never user photos.
            if (source != null) resolver.delete(source, null, null);
            if (enhanced != null) resolver.delete(enhanced, null, null);
        }
    }
}
