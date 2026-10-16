package yt.ren.slate.ui;

import yt.ren.slate.ui.vector.UIColor;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;

public class UIColors {
    private static float clamped(float value) {
        return value < 0.0f ? 0.0f : Floats.min(1.0f, value);
    }

    public static UIColor interpolate(UIColor first, UIColor second, float amount) {
        amount = clamped(amount);
        float inverse = 1.0f - amount;
        return new UIColor(inverse * first.red + amount * second.red, inverse * first.green + amount * second.green,
                inverse * first.blue + amount * second.blue, inverse * first.alpha + amount * second.alpha);
    }

    private static float wrappedHue(float hue) {
        hue = hue < 0.0f ? hue + 1.0f : hue;
        return 1.0f < hue ? hue - 1.0f : hue;
    }

    private static float hueChannel(float lower, float upper, float hue) {
        hue = wrappedHue(hue);
        float sixth = Float.intBitsToFloat(0x3e2aaaab);
        float twoThirds = Float.intBitsToFloat(0x3f2aaaab);
        float result;
        if (sixth > hue) {
            result = ((upper - lower) * hue) * 6.0f + lower;
        } else if (0.5f > hue) {
            result = upper;
        } else if (twoThirds > hue) {
            result = ((twoThirds - hue) * (upper - lower)) * 6.0f + lower;
        } else {
            result = lower;
        }
        return clamped(result);
    }

    public static UIColor hsl(float hue, float saturation, float lightness) {
        hue = Geometry.remainderFloat(hue, 1.0f);
        hue = hue < 0.0f ? hue + 1.0f : hue;
        saturation = clamped(saturation);
        lightness = clamped(lightness);
        float upper = lightness <= 0.5f ? (saturation + 1.0f) * lightness
                : (saturation + lightness) - lightness * saturation;
        float lower = (lightness + lightness) - upper;
        float third = Float.intBitsToFloat(0x3eaaaaab);
        return new UIColor(hueChannel(lower, upper, third + hue), hueChannel(lower, upper, hue),
                hueChannel(lower, upper, hue + Floats.negate(third)), (float) 255 / 255.0f);
    }
}
