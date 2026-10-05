package dev.gtos.photoenhancer;

/** Conservative luminance-only reconstruction. Colour and source geometry stay intact. */
public final class RestorationMath {
    private RestorationMath() {}
    public static float luminance(int rgb) {
        return .299f * ((rgb >> 16) & 255) + .587f * ((rgb >> 8) & 255) + .114f * (rgb & 255);
    }
    public static int reflect(int p, int size) {
        if (size <= 1) return 0;
        int period = 2 * size - 2;
        int q = Math.floorMod(p, period);
        return q < size ? q : period - q;
    }
    public static int blend(int rgb, float prediction, float originalY, float noise) {
        if (!Float.isFinite(prediction)) return rgb;
        // Never synthesize strong new edges: at most six brightness levels per channel.
        float delta = Math.max(-6f, Math.min(6f, (prediction - originalY) * .30f));
        delta += Math.max(-2f, Math.min(2f, noise));
        int d = Math.round(Math.max(-8f, Math.min(8f, delta)));
        int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + d));
        int g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + d));
        int b = Math.max(0, Math.min(255, (rgb & 255) + d));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
}
