package yt.ren.slate.util.math;

public class ScalarRounding {
    public static float ceilGameFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int exponent = (bits >>> 23) & 0xff;
        if (exponent >= 0x96) {
            return value;
        }
        if (exponent < 0x7f) {
            if ((bits & 0x80000000) != 0) {
                return Float.intBitsToFloat(0x80000000);
            }
            return bits == 0 ? value : 1.0f;
        }
        int mask = 0xffffffff << ((0x16 - exponent) & 31);
        int fraction = ~mask;
        if ((bits & fraction) == 0) {
            return value;
        }
        return Float.intBitsToFloat((bits + ((bits & 0x80000000) != 0 ? 0 : fraction)) & mask);
    }

    public static float floorGameFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int exponent = (bits >>> 23) & 0xff;
        if (exponent >= 0x96) {
            return value;
        }
        if (exponent < 0x7f) {
            if ((bits & 0x80000000) == 0) {
                return 0.0f;
            }
            return value == 0.0f ? value : -1.0f;
        }
        int mask = 0xffffffff << ((0x16 - exponent) & 31);
        int fraction = ~mask;
        if ((bits & fraction) == 0) {
            return value;
        }
        return Float.intBitsToFloat((bits + ((bits & 0x80000000) != 0 ? fraction : 0)) & mask);
    }
}
