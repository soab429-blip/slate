package yt.ren.slate.aim.rotation;

import yt.ren.slate.aim.AimState;
import yt.ren.slate.util.structs.Rotation;

public class RotationApply {
    public static float truncateRotationFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int exponent = (bits >>> 23) & 255;
        if (exponent < 150) {
            int shift = exponent < 127 ? 1 : exponent - 118;
            bits &= ~(0xffffffff >>> shift);
        }
        return Float.intBitsToFloat(bits);
    }

    public static float quantize(float value, float step) {
        float negative = Float.intBitsToFloat(Float.floatToRawIntBits(step) ^ 0x80000000);
        if (value <= negative || step <= value) {
            float quotient = value / step;
            return truncateRotationFloat(quotient) * step;
        }
        return 0.0f;
    }

    public static void accumulateMouseRotation(AimState state, Rotation delta) {
        state.mouseDelta = state.mouseDelta.add(delta);
    }

    public static float roundRotationFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int exponent = (bits >>> 23) & 255;
        if (exponent >= 150) {
            return value;
        }
        float absolute = Float.intBitsToFloat(bits & 0x7fffffff);
        float difference = 8388608.0f + absolute;
        if (exponent <= 125) {
            return value * 0.0f;
        }
        difference = (difference + -8388608.0f) - absolute;
        float result = absolute + difference;
        if (difference > 0.5f) {
            result = result + -1.0f;
        } else if (difference <= -0.5f) {
            result = result + 1.0f;
        }
        if ((bits & 0x80000000) != 0) {
            result = Float.intBitsToFloat(Float.floatToRawIntBits(result) ^ 0x80000000);
        }
        return result;
    }
}
