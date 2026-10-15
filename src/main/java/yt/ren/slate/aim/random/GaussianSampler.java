package yt.ren.slate.aim.random;

import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.structs.Rotation;

public class GaussianSampler {
    private static float bits(int value) {
        return Float.intBitsToFloat(value);
    }

    public static float logarithmGameFloat(float value) {
        int raw = Float.floatToRawIntBits(value);
        int adjustment = 0xffffff81;
        if (raw < 0x800000) {
            if (value == 0.0f) {
                return -1.0f / (value * value);
            }
            if ((raw & 0x80000000) != 0) {
                return Float.NaN;
            }
            value = value * 33554432.0f;
            raw = Float.floatToRawIntBits(value);
            adjustment = 0xffffff68;
        } else {
            if (Integer.compareUnsigned(raw, 0x7f7fffff) > 0) {
                return value;
            }
            if (raw == 0x3f800000) {
                return 0.0f;
            }
        }
        raw += 0x4afb0d;
        int exponent = (raw >>> 23) + adjustment;
        float f = bits((raw & 0x7fffff) + 0x3f3504f3) + -1.0f;
        float ratio = f / (2.0f + f);
        float square = ratio * ratio;
        float fourth = square * square;
        float first = square * (fourth * bits(0x3e91e9ee) + bits(0x3f2aaaaa));
        float second = fourth * (fourth * bits(0x3e789e26) + bits(0x3eccce13));
        float halfSquare = (0.5f * f) * f;
        float correction = (second + first + halfSquare) * ratio;
        float power = (float) exponent;
        float low = ((bits(0x3717f7d1) * power + correction) - halfSquare) + f;
        return power * bits(0x3f317180) + low;
    }

    public static float sampleCachedRandom(CachedRandomSample state, AimRandom random) {
        if (state.available) {
            state.available = false;
            return state.value;
        }
        float x;
        float y;
        float radius;
        do {
            float first = random.range(0.0f, 1.0f);
            x = (first + first) + -1.0f;
            float second = random.range(0.0f, 1.0f);
            y = (second + second) + -1.0f;
            radius = Geometry.angleLength(new Rotation(x, y));
        } while (radius >= 1.0f || radius == 0.0f);
        float factorSquared = (logarithmGameFloat(radius) * -2.0f) / radius;
        float factor = (float) Math.sqrt(factorSquared);
        state.value = y * factor;
        state.available = true;
        return x * factor;
    }
}
