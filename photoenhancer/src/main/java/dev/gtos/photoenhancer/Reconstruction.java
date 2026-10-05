package dev.gtos.photoenhancer;

import android.content.Context;
import android.graphics.Bitmap;
import java.nio.FloatBuffer;
import java.util.Collections;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class Reconstruction {
    // Official model has fixed 224x224 input and 672x672 output tensors.
    private static final int TILE = 224, HALO = 8, CORE = TILE - 2 * HALO;
    public static Bitmap enhance(Context context, Bitmap original) throws Exception {
        int w = original.getWidth(), h = original.getHeight();
        int lw = (w + 2) / 3, lh = (h + 2) / 3;
        Bitmap small = Bitmap.createScaledBitmap(original, lw, lh, true);
        int[] low = new int[lw * lh];
        small.getPixels(low, 0, lw, 0, 0, lw, lh);
        small.recycle();
        float[] y = new float[low.length];
        for (int i = 0; i < low.length; i++) y[i] = RestorationMath.luminance(low[i]) / 255f;
        int[] source = new int[w * h];
        original.getPixels(source, 0, w, 0, 0, w, h);
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            byte[] model;
            try (var stream = context.getAssets().open("reconstruction.onnx")) { model = stream.readAllBytes(); }
            try (OrtSession session = env.createSession(model, options)) {
                String inputName = session.getInputNames().iterator().next();
                for (int ty = 0; ty < lh; ty += CORE) for (int tx = 0; tx < lw; tx += CORE) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    float[] input = new float[TILE * TILE];
                    for (int py = 0; py < TILE; py++) for (int px = 0; px < TILE; px++) {
                        int sy = RestorationMath.reflect(ty + py - HALO, lh);
                        int sx = RestorationMath.reflect(tx + px - HALO, lw);
                        input[py * TILE + px] = y[sy * lw + sx];
                    }
                    try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[]{1, 1, TILE, TILE});
                         OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                        FloatBuffer prediction = ((OnnxTensor) result.get(0)).getFloatBuffer();
                        int bw = Math.min(CORE * 3, w - tx * 3), bh = Math.min(CORE * 3, h - ty * 3);
                        int[] tile = new int[bw * bh];
                        for (int py = 0; py < bh; py++)
                            for (int px = 0; px < bw; px++) {
                                int ox = tx * 3 + px, oy = ty * 3 + py, i = oy * w + ox;
                                float base = RestorationMath.luminance(source[i]);
                                float sum = base, weight = 1f;
                                // Edge-aware small-neighbour reduction, with a strict brightness cap.
                                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                                    if (dx == 0 && dy == 0) continue;
                                    float neighbour = RestorationMath.luminance(source[RestorationMath.reflect(oy + dy, h) * w + RestorationMath.reflect(ox + dx, w)]);
                                    float difference = Math.abs(neighbour - base);
                                    float wt = Math.max(0f, 1f - difference / 16f);
                                    sum += neighbour * wt; weight += wt;
                                }
                                float value = prediction.get((py + HALO * 3) * TILE * 3 + px + HALO * 3) * 255f;
                                tile[py * bw + px] = RestorationMath.blend(source[i], value, base, (sum / weight - base) * .45f);
                            }
                        output.setPixels(tile, 0, bw, tx * 3, ty * 3, bw, bh);
                    }
                }
            }
        }
        return output;
    }
}
