package yt.ren.slate.util.math;

import yt.ren.slate.util.structs.Rotation;

public class MathUtil {
    private static final float MOVE_FRICTION = Float.intBitsToFloat(0x3f7ae148);
    private static final float MOVE_EPSILON = Float.intBitsToFloat(0x38d1b717);
    private static final float MOVE_FACTOR = Float.intBitsToFloat(0x3f7ffffd);
    private static final double PI = Double.longBitsToDouble(0x400921fb54442d18L);
    private static final double NEG_PI = Double.longBitsToDouble(0xc00921fb54442d18L);

    public static Vec3 movementAcceleration(Rotation rotation, float forward, float strafe, float factor) {
        float f = forward * MOVE_FRICTION;
        float s = strafe * MOVE_FRICTION;
        float length = s * s + f * f;
        if (MOVE_EPSILON > length) {
            return Vec3.ZERO;
        }
        float amount = factor * MOVE_FACTOR;
        amount = amount / Math.max(1.0f, (float) Math.sqrt(length));
        s = s * amount;
        f = f * amount;
        double angle = (double) rotation.yaw * PI / 180.0;
        float radians = (float) angle;
        float sine = ScalarTrig.sine(radians);
        float cosine = ScalarTrig.cosine(radians);
        float x = s * cosine - f * sine;
        float z = f * cosine + s * sine;
        return new Vec3(x, 0.0, z);
    }

    public static Vec3 lookDirection(Rotation rotation) {
        double yawDouble = (double) rotation.yaw * NEG_PI / 180.0 + NEG_PI;
        float yaw = (float) yawDouble;
        float cosineYaw = ScalarTrig.cosine(yaw);
        float sineYaw = ScalarTrig.sine(yaw);
        double pitchDouble = (double) rotation.pitch * NEG_PI / 180.0;
        float pitch = (float) pitchDouble;
        float cosinePitch = Float.intBitsToFloat(Float.floatToRawIntBits(ScalarTrig.cosine(pitch)) ^ 0x80000000);
        float sinePitch = ScalarTrig.sine(pitch);
        float x = sineYaw * cosinePitch;
        float z = cosinePitch * cosineYaw;
        return new Vec3(x, sinePitch, z);
    }
}
