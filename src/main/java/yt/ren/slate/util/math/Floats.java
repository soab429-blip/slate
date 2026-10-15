package yt.ren.slate.util.math;

public class Floats {
    @SuppressWarnings("ManualMinMaxCalculation")
    public static float min(float a, float b) {
        return (a < b) ? a : b;
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    public static float max(float a, float b) {
        return (a > b) ? a : b;
    }

    public static float magnitude(float value) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(value) & 0x7fffffff);
    }

    public static float negate(float value) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(value) ^ 0x80000000);
    }
}
