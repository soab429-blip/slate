package yt.ren.slate.util.math;

import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

public class Geometry {
    private static final double INF = Double.longBitsToDouble(0x7ff0000000000000L);

    @SuppressWarnings("ManualMinMaxCalculation")
    private static double sseMin(double a, double b) {
        return (a < b) ? a : b;
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    private static double sseMax(double a, double b) {
        return (a > b) ? a : b;
    }

    private static double round32(double value) {
        return (float) value;
    }

    public static Bounds orderedBounds(Vec3 first, Vec3 second) {
        double minX = Double.isNaN(first.x) ? second.x : sseMin(second.x, first.x);
        double minY = Double.isNaN(first.y) ? second.y : sseMin(second.y, first.y);
        double maxX = Double.isNaN(first.x) ? second.x : sseMax(second.x, first.x);
        double maxY = Double.isNaN(first.y) ? second.y : sseMax(second.y, first.y);
        double minZ = Double.isNaN(first.z) ? second.z : sseMin(second.z, first.z);
        double maxZ = Double.isNaN(first.z) ? second.z : sseMax(second.z, first.z);
        return new Bounds(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    public static Bounds expandBounds(Bounds bounds, double amount) {
        return new Bounds(
                new Vec3(bounds.minimum.x - amount, bounds.minimum.y - amount, bounds.minimum.z - amount),
                new Vec3(bounds.maximum.x + amount, bounds.maximum.y + amount, bounds.maximum.z + amount));
    }

    public static Vec3 nearestPoint(Bounds bounds, Vec3 point) {
        double x = (point.x < bounds.minimum.x) ? bounds.minimum.x : sseMin(bounds.maximum.x, point.x);
        double y = (point.y < bounds.minimum.y) ? bounds.minimum.y : sseMin(bounds.maximum.y, point.y);
        double z = (point.z < bounds.minimum.z) ? bounds.minimum.z : sseMin(bounds.maximum.z, point.z);
        return new Vec3(x, y, z);
    }

    public static boolean overlaps(Bounds left, Bounds right) {
        return right.maximum.x > left.minimum.x && left.maximum.x > right.minimum.x
                && right.maximum.y > left.minimum.y && left.maximum.y > right.minimum.y
                && right.maximum.z > left.minimum.z && left.maximum.z > right.minimum.z;
    }

    public static boolean containsStrict(Bounds bounds, Vec3 point) {
        return point.x > bounds.minimum.x && bounds.maximum.x > point.x
                && point.y > bounds.minimum.y && bounds.maximum.y > point.y
                && point.z > bounds.minimum.z && bounds.maximum.z > point.z;
    }

    public static Vec3 reciprocalComponents(Vec3 value) {
        return new Vec3(1.0 / value.x, 1.0 / value.y, 1.0 / value.z);
    }

    private static float[] segmentBoundsInterval(Bounds b, Vec3 origin, Vec3 reciprocal) {
        double x0 = round32((b.minimum.x - origin.x) * reciprocal.x);
        double x1 = round32((b.maximum.x - origin.x) * reciprocal.x);
        double low = round32(sseMin(sseMax(x0, 0.0), sseMax(x1, 0.0)));
        double high = round32(sseMax(sseMin(x0, INF), sseMin(x1, INF)));
        double y0 = round32((b.minimum.y - origin.y) * reciprocal.y);
        double y1 = round32((b.maximum.y - origin.y) * reciprocal.y);
        low = round32(sseMin(sseMax(y0, low), sseMax(y1, low)));
        high = round32(sseMax(sseMin(y0, high), sseMin(y1, high)));
        double z0 = round32((b.minimum.z - origin.z) * reciprocal.z);
        double z1 = round32((b.maximum.z - origin.z) * reciprocal.z);
        double lower0 = Double.isNaN(z0) ? low : sseMax(low, z0);
        double lower1 = Double.isNaN(z1) ? low : sseMax(low, z1);
        low = Double.isNaN(lower0) ? lower1 : sseMin(lower1, lower0);
        double upper0 = Double.isNaN(z0) ? high : sseMin(high, z0);
        double upper1 = Double.isNaN(z1) ? high : sseMin(high, z1);
        high = Double.isNaN(upper0) ? upper1 : sseMax(upper1, upper0);
        return new float[]{(float) low, (float) high};
    }

    public static boolean segmentIntersectsBounds(Bounds b, Vec3 origin, Vec3 reciprocal) {
        float[] interval = segmentBoundsInterval(b, origin, reciprocal);
        float first = interval[0];
        float last = interval[1];
        return (first < last) && (first <= 1.0f);
    }

    public static Vec3 segmentBoundsIntersection(Bounds b, Vec3 origin, Vec3 segment) {
        Vec3 inverse = new Vec3(1.0 / segment.x, 1.0 / segment.y, 1.0 / segment.z);
        float[] interval = segmentBoundsInterval(b, origin, inverse);
        float first = interval[0];
        float last = interval[1];
        boolean accepted = !(last <= first) && !(1.0f < first);
        if (!accepted) {
            return null;
        }
        return new Vec3(origin.x + segment.x * (double) first,
                origin.y + segment.y * (double) first,
                origin.z + segment.z * (double) first);
    }

    public static double distanceSquared(Vec3 left, Vec3 right) {
        double x = left.x - right.x;
        double y = left.y - right.y;
        double z = left.z - right.z;
        return z * z + (x * x + y * y);
    }

    public static double distance(Vec3 left, Vec3 right) {
        double x = left.x - right.x;
        double y = left.y - right.y;
        double z = left.z - right.z;
        return Math.sqrt(z * z + (x * x + y * y));
    }

    public static double interpolateScalar(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    public static float remainderFloat(float value, float divisor) {
        int valueBits = Float.floatToRawIntBits(value);
        int divisorBits = Float.floatToRawIntBits(divisor);
        int exponent = (valueBits >>> 23) & 255;
        int divisorExponent = (divisorBits >>> 23) & 255;
        if ((divisorBits << 1) == 0 || exponent == 255 || Float.isNaN(divisor)) {
            return 1.0f;
        }
        int valueTwice = valueBits << 1;
        int divisorTwice = divisorBits << 1;
        if (Integer.compareUnsigned(valueTwice, divisorTwice) <= 0) {
            return (valueTwice == divisorTwice) ? value * 0.0f : value;
        }
        int[] expBox = {exponent};
        int significand = normalizeFloat(valueBits, expBox);
        exponent = expBox[0];
        expBox[0] = divisorExponent;
        int denominator = normalizeFloat(divisorBits, expBox);
        divisorExponent = expBox[0];
        while (exponent > divisorExponent) {
            int difference = significand - denominator;
            if ((difference & 0x80000000) == 0) {
                significand = difference;
                if (significand == 0) {
                    return value * 0.0f;
                }
            }
            significand <<= 1;
            --exponent;
        }
        int difference = significand - denominator;
        if ((difference & 0x80000000) == 0) {
            significand = difference;
            if (significand == 0) {
                return value * 0.0f;
            }
        }
        while (Integer.compareUnsigned(significand, 0x800000) < 0) {
            significand <<= 1;
            --exponent;
        }
        int result;
        if (exponent > 0) {
            result = (exponent << 23) | (significand - 0x800000);
        } else {
            result = significand >>> ((1 - exponent) & 31);
        }
        return Float.intBitsToFloat(result | (valueBits & 0x80000000));
    }

    private static int normalizeFloat(int bits, int[] exp) {
        if (exp[0] != 0) {
            return (bits & 0x7fffff) | 0x800000;
        }
        int shifted = bits << 9;
        while ((shifted & 0x80000000) == 0) {
            shifted <<= 1;
            --exp[0];
        }
        return bits << ((1 - exp[0]) & 31);
    }

    public static double remainderDouble(double value, double divisor) {
        long valueBits = Double.doubleToRawLongBits(value);
        long divisorBits = Double.doubleToRawLongBits(divisor);
        int exponent = (int) ((valueBits >>> 52) & 2047);
        int divisorExponent = (int) ((divisorBits >>> 52) & 2047);
        if ((divisorBits << 1) == 0 || exponent == 2047 || Double.isNaN(divisor)) {
            return 1.0;
        }
        long valueTwice = valueBits << 1;
        long divisorTwice = divisorBits << 1;
        if (Long.compareUnsigned(valueTwice, divisorTwice) <= 0) {
            return (valueTwice == divisorTwice) ? value * 0.0 : value;
        }
        int[] expBox = {exponent};
        long significand = normalizeDouble(valueBits, expBox);
        exponent = expBox[0];
        expBox[0] = divisorExponent;
        long denominator = normalizeDouble(divisorBits, expBox);
        divisorExponent = expBox[0];
        while (exponent > divisorExponent) {
            long difference = significand - denominator;
            if ((difference & 0x8000000000000000L) == 0) {
                significand = difference;
                if (significand == 0) {
                    return value * 0.0;
                }
            }
            significand <<= 1;
            --exponent;
        }
        long difference = significand - denominator;
        if ((difference & 0x8000000000000000L) == 0) {
            significand = difference;
            if (significand == 0) {
                return value * 0.0;
            }
        }
        while (Long.compareUnsigned(significand, 0x10000000000000L) < 0) {
            significand <<= 1;
            --exponent;
        }
        long result;
        if (exponent > 0) {
            result = ((long) exponent << 52) | (significand - 0x10000000000000L);
        } else {
            result = significand >>> ((1 - exponent) & 63);
        }
        return Double.longBitsToDouble(result | (valueBits & 0x8000000000000000L));
    }

    private static long normalizeDouble(long bits, int[] exp) {
        if (exp[0] != 0) {
            return (bits & 0xfffffffffffffL) | 0x10000000000000L;
        }
        long shifted = bits << 12;
        while ((shifted & 0x8000000000000000L) == 0) {
            shifted <<= 1;
            --exp[0];
        }
        return bits << ((1 - exp[0]) & 63);
    }

    public static float wrapYaw(float degrees) {
        float result = remainderFloat(degrees, 360.0f);
        if (result >= 180.0f) {
            result = result - 360.0f;
        } else if (result < -180.0f) {
            result = result + 360.0f;
        }
        return result;
    }

    private static final int[] PERMUTATION = {
            151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225,
            140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148,
            247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32,
            57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175,
            74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122,
            60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54,
            65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169,
            200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
            52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212,
            207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213,
            119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
            129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104,
            218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241,
            81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157,
            184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93,
            222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180
    };

    public static float gradientNoise(float coordinate) {
        int truncated = (int) coordinate;
        float converted = (float) truncated;
        int cell = truncated;
        if (!(coordinate > converted)) {
            --cell;
        }
        float cellFloat = (float) cell;
        float fraction = coordinate - cellFloat;
        int first = PERMUTATION[cell & 255];
        int second = PERMUTATION[(cell + 1) & 255];
        float gradientA = (first & 7) + 1;
        float gradientB = (second & 7) + 1;
        if ((first & 8) != 0) {
            gradientA = -gradientA;
        }
        if ((second & 8) != 0) {
            gradientB = -gradientB;
        }
        float fade = fraction * fraction * fraction;
        float polynomial = (6.0f * fraction - 15.0f) * fraction + 10.0f;
        fade = fade * polynomial;
        gradientA = gradientA * fraction;
        float gradientDelta = (-1.0f + fraction) * gradientB - gradientA;
        return (fade * gradientDelta + gradientA) * 0.188f;
    }

    public static Rotation directionBetween(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double pi = 3.141592653589793;
        double azimuth = CrtMath.atan2(dz, dx);
        float yaw = (float) ((azimuth * 180.0) / pi - 90.0);
        double elevation = CrtMath.atan2(dy, horizontal);
        float pitch = (float) ((elevation * -180.0) / pi);
        return new Rotation(yaw, pitch);
    }

    public static float angleLength(Rotation value) {
        int yawBits = Float.floatToRawIntBits(value.yaw) & 0x7fffffff;
        int pitchBits = Float.floatToRawIntBits(value.pitch) & 0x7fffffff;
        if (yawBits == 0x7f800000 || pitchBits == 0x7f800000) {
            return Float.intBitsToFloat(0x7f800000);
        }
        if (Integer.compareUnsigned(yawBits, 0x7f800000) > 0
                || Integer.compareUnsigned(pitchBits, 0x7f800000) > 0) {
            return Float.intBitsToFloat(0x7fc00000);
        }
        double yaw = value.yaw;
        double pitch = value.pitch;
        return (float) Math.sqrt(pitch * pitch + yaw * yaw);
    }
}
