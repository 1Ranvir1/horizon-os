package dev.gtos.photoenhancer;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.concurrent.Executors;

public class EnhanceService extends Service {
    public static final String ACTION = "dev.gtos.photoenhancer.ENHANCE";
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String CHANNEL = "photo-processing";
    private static final int ID = 71;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Photo enhancement", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(String text, Uri result) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("GT Photo Enhance")
            .setContentText(text).setOnlyAlertOnce(true);
        if (result != null) {
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(result, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.setContentIntent(PendingIntent.getActivity(this, 1, view, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, notice("Processing photo on your phone…", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        Uri uri = intent == null ? null : intent.getData();
        boolean manual = intent != null && intent.getBooleanExtra("manual", false);
        executor.execute(() -> {
            Uri output = null;
            try {
                if (uri != null && "content".equals(uri.getScheme()) && (manual || getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true))) {
                    output = process(uri);
                }
            } catch (Exception | OutOfMemoryError failure) {
                // Failure never damages, renames, or deletes the source photograph.
                Log.e("GTPhotoEnhance", "Enhancement failed; original retained", failure);
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "Could not enhance this photo. Original is safe.").apply();
            } finally {
                if (stopSelfResult(startId)) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    if (output != null && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) getSystemService(NotificationManager.class).notify(72, notice("Enhanced copy saved. Original retained.", output));
                }
            }
        });
        return START_NOT_STICKY;
    }
    private Uri process(Uri source) throws Exception {
        if (!"image/jpeg".equals(getContentResolver().getType(source))) {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "This format is not supported yet. Original retained.").apply(); return null;
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "Processing on your phone. Original retained.").apply();
        String name = "Photo.jpg";
        long taken = System.currentTimeMillis();
        try (Cursor cursor = getContentResolver().query(source, new String[]{MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String candidate = cursor.getString(0);
                if (candidate != null) name = candidate;
                if (!cursor.isNull(1) && cursor.getLong(1) > 0) taken = cursor.getLong(1);
            }
        }
        File input = File.createTempFile("original-", ".jpg", getCacheDir());
        File processed = File.createTempFile("enhanced-", ".jpg", getCacheDir());
        Uri pending = null;
        Bitmap original = null, restored = null;
        long began = android.os.SystemClock.elapsedRealtime();
        try {
            try (InputStream in = getContentResolver().openInputStream(source); OutputStream out = new FileOutputStream(input)) {
                if (in == null) throw new IOException("No input stream");
                byte[] buffer = new byte[65536]; long size = 0; int count;
                while ((count = in.read(buffer)) != -1) {
                    size += count;
                    if (size > 64L * 1024 * 1024) throw new IOException("Photo exceeds memory-safe input limit");
                    out.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(input.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 64_000_000) throw new IOException("Invalid image dimensions");
            // Leave larger originals untouched rather than silently reducing their resolution.
            if ((long) bounds.outWidth * bounds.outHeight > 12_500_000) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "Photo exceeds 12.5 MP processing limit. Original retained.").apply(); return null;
            }
            original = BitmapFactory.decodeFile(input.getPath());
            if (original == null) throw new IOException("Cannot decode image");
            if (original.hasGainmap()) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "Ultra HDR photo retained without flattening its HDR data.").apply(); return null;
            }
            boolean advanced = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("advanced", true);
            restored = advanced ? AdvancedRestoration.enhance(this, original) : Reconstruction.enhance(this, original);
            try (OutputStream out = new FileOutputStream(processed)) {
                if (!restored.compress(Bitmap.CompressFormat.JPEG, 98, out)) throw new IOException("JPEG encode failed");
            }
            ExifInterface oldExif = new ExifInterface(input), newExif = new ExifInterface(processed);
            String[] tags = {"Orientation", "DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "SubSecTimeOriginal", "Make", "Model", "LensMake", "LensModel", "FocalLength", "FocalLengthIn35mmFilm", "ExposureTime", "FNumber", "PhotographicSensitivity", "ISOSpeedRatings", "Flash", "WhiteBalance", "ExposureBiasValue", "MeteringMode", "ExposureProgram", "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod"};
            for (String tag : tags) { String value = oldExif.getAttribute(tag); if (value != null) newExif.setAttribute(tag, value); }
            newExif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, advanced ? "GT AI Reconstruct: AI-estimated detail; original preserved separately" : "GT Photo Enhance: conservative AI luminance reconstruction; original preserved separately");
            newExif.saveAttributes();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name.replaceFirst("(?i)\\.jpe?g$", "") + (advanced ? "_AI_reconstructed.jpg" : "_enhanced.jpg"));
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/Enhanced/");
            values.put(MediaStore.Images.Media.DATE_TAKEN, taken + 1);
            values.put(MediaStore.Images.Media.WIDTH, restored.getWidth());
            values.put(MediaStore.Images.Media.HEIGHT, restored.getHeight());
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            pending = getContentResolver().insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (pending == null) throw new IOException("Cannot create enhanced copy");
            try (InputStream in = new FileInputStream(processed); OutputStream out = getContentResolver().openOutputStream(pending)) {
                if (out == null) throw new IOException("Cannot save enhanced copy");
                in.transferTo(out);
            }
            ContentValues publish = new ContentValues(); publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (getContentResolver().update(pending, publish, null, null) != 1) throw new IOException("Cannot publish enhanced copy");
            Uri finished = pending; pending = null;
            long elapsed = android.os.SystemClock.elapsedRealtime() - began;
            Log.i("GTPhotoEnhance", "Published enhanced copy: " + restored.getWidth() + "x" + restored.getHeight() + " in " + elapsed + " ms");
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("last_result", "Enhanced copy saved in " + (elapsed / 1000f) + " seconds. Original retained.").apply();
            return finished;
        } finally {
            if (pending != null) getContentResolver().delete(pending, null, null);
            if (original != null) original.recycle(); if (restored != null) restored.recycle();
            if (!input.delete()) Log.w("GTPhotoEnhance", "Temporary input cleanup deferred");
            if (!processed.delete()) Log.w("GTPhotoEnhance", "Temporary output cleanup deferred");
        }
    }
    @Override public void onDestroy() { executor.shutdown(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
