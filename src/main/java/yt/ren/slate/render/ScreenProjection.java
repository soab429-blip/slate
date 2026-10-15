package yt.ren.slate.render;

import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.util.math.Vec3;

import java.nio.FloatBuffer;

public class ScreenProjection {
    public static boolean project(ScreenRenderEvent event, Vec3 point, float[] screen, float[] scale) {
        float x = (float) point.x;
        float y = (float) point.y;
        float z = (float) point.z;
        FloatBuffer model = event.modelview;
        FloatBuffer projection = event.projection;
        float mx = transform(model, 0, x, y, z);
        float my = transform(model, 1, x, y, z);
        float mz = transform(model, 2, x, y, z);
        float wy = y * model.get(7);
        float wx = x * model.get(3);
        float wxy = wx + wy;
        float wz = z * model.get(11);
        float mw = (wz + wxy) + model.get(15);
        float w = project(projection, 3, mx, my, mz, mw);
        if (w == 0.0f) {
            return false;
        }
        float depth = project(projection, 2, mx, my, mz, mw);
        float factor = (1.0f / w) * 0.5f;
        float normalizedDepth = depth * factor + 0.5f;
        if (normalizedDepth <= 0.0f || normalizedDepth >= 1.0f) {
            return false;
        }
        float py = project(projection, 1, mx, my, mz, mw) * factor + 0.5f;
        float xy = mx * projection.get(0) + my * projection.get(4);
        float xyz = mz * projection.get(8) + xy;
        float px = (mw * projection.get(12) + xyz) * factor + 0.5f;
        screen[0] = px * event.width;
        screen[1] = event.height - py * event.height;
        if (scale != null) {
            scale[0] = factor;
        }
        return true;
    }

    private static float transform(FloatBuffer model, int row, float x, float y, float z) {
        float vertical = model.get(row + 4) * y;
        float horizontal = model.get(row) * x;
        float sum = horizontal + vertical;
        float depth = model.get(row + 8) * z;
        return (depth + sum) + model.get(row + 12);
    }

    private static float project(FloatBuffer projection, int row, float mx, float my, float mz, float mw) {
        float vertical = projection.get(row + 4) * my;
        float horizontal = projection.get(row) * mx;
        float sum = horizontal + vertical;
        float depth = projection.get(row + 8) * mz;
        float xyz = depth + sum;
        float fourth = projection.get(row + 12) * mw;
        return fourth + xyz;
    }

    public static float[] nameColorRgb(int code) {
        float low = Float.intBitsToFloat(0x3ea8f5c3);
        float high = Float.intBitsToFloat(0x3f2b851f);
        switch (code) {
            case '0':
                return new float[]{0.0f, 0.0f, 0.0f};
            case '1':
                return new float[]{0.0f, 0.0f, high};
            case '2':
                return new float[]{0.0f, high, 0.0f};
            case '3':
                return new float[]{0.0f, high, high};
            case '4':
                return new float[]{high, 0.0f, 0.0f};
            case '5':
                return new float[]{high, 0.0f, high};
            case '6':
                return new float[]{1.0f, high, 0.0f};
            case '7':
                return new float[]{high, high, high};
            case '8':
                return new float[]{low, low, low};
            case '9':
                return new float[]{low, low, 1.0f};
            case 'a':
                return new float[]{low, 1.0f, low};
            case 'b':
                return new float[]{low, 1.0f, 1.0f};
            case 'c':
                return new float[]{1.0f, low, low};
            case 'd':
                return new float[]{1.0f, low, 1.0f};
            case 'e':
                return new float[]{1.0f, 1.0f, low};
            default:
                return new float[]{1.0f, 1.0f, 1.0f};
        }
    }
}
