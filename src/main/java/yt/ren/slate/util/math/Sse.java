package yt.ren.slate.util.math;

public class Sse {
    public static int cvttss2si(float value) {
        if (Float.isNaN(value) || value >= 2147483648.0f || value < -2147483648.0f) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    public static int cvttsd2si(double value) {
        if (Double.isNaN(value) || value >= 2147483648.0 || value <= -2147483649.0) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    public static long cvttss2si64(float value) {
        if (Float.isNaN(value) || value >= 9.223372036854775807E18f || value < -9.223372036854775808E18f) {
            return Long.MIN_VALUE;
        }
        return (long) value;
    }

    public static long cvttsd2si64(double value) {
        if (Double.isNaN(value) || value >= 9.223372036854775807E18 || value < -9.223372036854775808E18) {
            return Long.MIN_VALUE;
        }
        return (long) value;
    }
}
