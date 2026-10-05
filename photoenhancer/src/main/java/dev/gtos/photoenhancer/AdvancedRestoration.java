package dev.gtos.photoenhancer;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.EnumSet;
import ai.onnxruntime.*;
import ai.onnxruntime.providers.NNAPIFlags;

/** Real-ESRGAN general-image restoration, without face synthesis or object generation. */
public final class AdvancedRestoration {
    private static final int TILE = 256, HALO = 36, CORE = TILE - 2 * HALO, MAX_INPUT = 512;
    private AdvancedRestoration() {}
    public static Bitmap enhance(Context context, Bitmap original) throws Exception {
        float ratio = Math.min(1f, (float) MAX_INPUT / Math.max(original.getWidth(), original.getHeight()));
        int sw = Math.max(1, Math.round(original.getWidth() * ratio));
        int sh = Math.max(1, Math.round(original.getHeight() * ratio));
        Bitmap inputImage = Bitmap.createScaledBitmap(original, sw, sh, true);
        int[] inputPixels = new int[sw * sh]; inputImage.getPixels(inputPixels, 0, sw, 0, 0, sw, sh);
        if (inputImage != original) inputImage.recycle();
        int ow = sw * 4, oh = sh * 4;
        Bitmap base = Bitmap.createScaledBitmap(original, ow, oh, true);
        int[] source = new int[ow * oh]; base.getPixels(source, 0, ow, 0, 0, ow, oh);
        if (base != original) base.recycle();
        Bitmap output = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        byte[] weights;
        try (var stream = context.getAssets().open("restoration-advanced.onnx")) { weights = stream.readAllBytes(); }
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(3); options.setInterOpNumThreads(1);
        OrtSession session;
        try {
            options.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.CPU_DISABLED));
            session = env.createSession(weights, options);
            Log.i("GTPhotoEnhance", "Advanced restoration session: NNAPI requested, CPU fallback available");
        } catch (OrtException unavailable) {
            options.close(); options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(3); options.setInterOpNumThreads(1);
            session = env.createSession(weights, options);
            Log.i("GTPhotoEnhance", "Advanced restoration session: CPU");
        }
        long deadline = android.os.SystemClock.elapsedRealtime() + 180000;
        try (OrtSession active = session; OrtSession.SessionOptions activeOptions = options) {
            String name = active.getInputNames().iterator().next();
            int inPlane = TILE * TILE, outPlane = TILE * TILE * 16;
            for (int sy = 0; sy < sh; sy += CORE) for (int sx = 0; sx < sw; sx += CORE) {
                if (android.os.SystemClock.elapsedRealtime() > deadline) throw new java.io.IOException("Restoration time limit; original retained");
                float[] tile = new float[inPlane * 3];
                for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) {
                    int rgb = inputPixels[RestorationMath.reflect(sy + y - HALO, sh) * sw + RestorationMath.reflect(sx + x - HALO, sw)];
                    int i = y * TILE + x;
                    tile[i] = ((rgb >> 16) & 255) / 255f;
                    tile[inPlane + i] = ((rgb >> 8) & 255) / 255f;
                    tile[2 * inPlane + i] = (rgb & 255) / 255f;
                }
                try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tile), new long[]{1, 3, TILE, TILE});
                     OrtSession.Result result = active.run(Collections.singletonMap(name, tensor))) {
                    FloatBuffer predicted = ((OnnxTensor) result.get(0)).getFloatBuffer();
                    int bw = Math.min(CORE * 4, ow - sx * 4), bh = Math.min(CORE * 4, oh - sy * 4);
                    int[] block = new int[bw * bh];
                    for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                        int i = (y + HALO * 4) * TILE * 4 + x + HALO * 4;
                        int rgb = source[(sy * 4 + y) * ow + sx * 4 + x];
                        int r = reconstruct((rgb >> 16) & 255, predicted.get(i) * 255f);
                        int g = reconstruct((rgb >> 8) & 255, predicted.get(outPlane + i) * 255f);
                        int b = reconstruct(rgb & 255, predicted.get(2 * outPlane + i) * 255f);
                        block[y * bw + x] = 0xff000000 | r << 16 | g << 8 | b;
                    }
                    output.setPixels(block, 0, bw, sx * 4, sy * 4, bw, bh);
                }
            }
        }
        float upscale = Math.max(1f, 1080f / Math.min(ow, oh));
        int fw = Math.round(ow * upscale), fh = Math.round(oh * upscale);
        if ((long) fw * fh > 12_500_000) { output.recycle(); throw new java.io.IOException("Panorama exceeds safe restoration size"); }
        if (fw != ow || fh != oh) {
            Bitmap resized = Bitmap.createScaledBitmap(output, fw, fh, true); output.recycle(); return resized;
        }
        return output;
    }
    public static int reconstruct(int source, float prediction) {
        if (!Float.isFinite(prediction)) return source;
        int delta = Math.round(Math.max(-40f, Math.min(40f, (prediction - source) * .80f)));
        return Math.max(0, Math.min(255, source + delta));
    }
}
