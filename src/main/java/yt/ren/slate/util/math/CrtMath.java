package yt.ren.slate.util.math;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;

@SuppressWarnings("unused")
public class CrtMath {
    private static final long ABS = 0x7fffffffffffffffL;
    private static final long MANTISSA = 0xfffffffffffffL;
    private static final double INFINITY = Double.longBitsToDouble(0x7ff0000000000000L);

    private static double bits(long value) {
        return Double.longBitsToDouble(value);
    }

    private static final double ONE = 1.0;
    private static final double HALF = 0.5;
    private static final double P_A = bits(0x3f0951665d321061L);
    private static final double P_B = bits(0x3f51e5f887a62135L);
    private static final double P_C = bits(0x3fac28d390c29690L);
    private static final double P_D = bits(0x3fd1a2bec1b7ef59L);
    private static final double P_E = bits(0x3fdc7b297e269eacL);
    private static final double P_F = bits(0x3fcd1e4180029834L);
    private static final double Q_A = bits(0x3fbb1a422982ce76L);
    private static final double Q_B = bits(0x3fee324ab418f78dL);
    private static final double Q_C = bits(0x40062021571dccfcL);
    private static final double Q_D = bits(0x400a4646f903cdeaL);
    private static final double Q_E = bits(0x3ff5d6b12001f228L);
    private static final double PIO2_LO = bits(0x3c91a62633145c07L);
    private static final double PIO2_HI = bits(0x3ff921fb54442d18L);
    private static final double PI = bits(0x400921fb54442d18L);
    private static final double DOMAIN_NAN = bits(0xfff8000000000000L);

    public static final int CPU_DISPATCH = detectDispatch();

    public static final boolean FMA3 = (CPU_DISPATCH & 3) == 3;

    public static final boolean FMA_AVX = CPU_DISPATCH != 0;

    private static int detectDispatch() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            boolean avx2 = Integer.parseInt(bean.getVMOption("UseAVX").getValue()) >= 2;
            boolean fma;
            try {
                fma = "true".equals(bean.getVMOption("UseFMA").getValue());
            } catch (IllegalArgumentException missing) {
                fma = avx2;
            }
            if (!fma) {
                return 0;
            }
            return avx2 ? 3 : 1;
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    public static double fma(double a, double b, double c) {
        if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c) || Double.isInfinite(a) || Double.isInfinite(b)
                || Double.isInfinite(c)) {
            return a * b + c;
        }
        BigDecimal exact = new BigDecimal(a).multiply(new BigDecimal(b))
                .add(new BigDecimal(c));
        if (exact.signum() == 0) {
            double product = a * b;
            boolean negativeProduct = (Double.doubleToRawLongBits(product) < 0) || (product == 0.0
                    && ((Double.doubleToRawLongBits(a) ^ Double.doubleToRawLongBits(b)) < 0));
            boolean negativeAddend = Double.doubleToRawLongBits(c) < 0;
            return negativeProduct && negativeAddend && c == 0.0 ? -0.0 : 0.0;
        }
        return exact.doubleValue();
    }

    public static double acos(double x) {
        return FMA3 ? acosFma3(x) : acosSse2(x);
    }

    public static double acosSse2(double x) {
        long raw = Double.doubleToRawLongBits(x);
        long exponent = (raw >>> 52) & 0x7ff;
        if ((raw & ABS) > 0x7ff0000000000000L) {
            return bits(raw | 0x8000000000000L);
        }
        if (exponent < 0x3c7) {
            return PIO2_HI;
        }
        if (exponent >= 0x3ff) {
            if (x == ONE) {
                return 0.0;
            }
            if (x == -ONE) {
                return PI;
            }
            return DOMAIN_NAN;
        }
        double magnitude = raw < 0 ? -x : x;
        int large;
        double z;
        double s = 0.0;
        if (exponent >= 0x3fe) {
            large = 1;
            z = (ONE - magnitude) * HALF;
            s = Math.sqrt(z);
        } else {
            large = 0;
            z = magnitude * magnitude;
        }
        double p = z * P_A;
        double q = z * Q_A;
        p = p + P_B;
        q = q - Q_B;
        p = p * z;
        q = q * z;
        p = p - P_C;
        q = q + Q_C;
        p = p * z;
        q = q * z;
        p = p + P_D;
        q = q - Q_D;
        p = p * z;
        q = q * z;
        p = p - P_E;
        q = q + Q_E;
        p = p * z;
        p = p + P_F;
        p = p * z;
        double r = p / q;
        if (large == 0) {
            double w = PIO2_LO - r * x;
            return PIO2_HI - (x - w);
        }
        if (raw < 0) {
            double w = s * r - PIO2_LO;
            w = w + s;
            w = w + w;
            return PI - w;
        }
        double twoSR = (s + s) * r;
        double df = bits(Double.doubleToRawLongBits(s) & 0xffffffff00000000L);
        double dfSquared = df * df;
        double sum = df + s;
        double twoDf = df + df;
        double c = (z - dfSquared) / sum;
        c = c + c;
        double w = twoSR + c;
        return w + twoDf;
    }

    public static double acosFma3(double x) {
        long raw = Double.doubleToRawLongBits(x);
        long exponent = (raw >>> 52) & 0x7ff;
        if ((raw & ABS) > 0x7ff0000000000000L) {
            return bits(raw | 0x8000000000000L);
        }
        if (exponent < 0x3c7) {
            return PIO2_HI;
        }
        if (exponent >= 0x3ff) {
            if (x == ONE) {
                return 0.0;
            }
            if (x == -ONE) {
                return PI;
            }
            return DOMAIN_NAN;
        }
        double magnitude = raw < 0 ? bits(raw ^ 0x8000000000000000L) : x;
        int large;
        double z;
        double s = 0.0;
        if (exponent >= 0x3fe) {
            large = 1;
            z = (ONE - magnitude) * HALF;
            s = Math.sqrt(z);
        } else {
            large = 0;
            z = magnitude * magnitude;
        }
        double p = fma(P_A, z, P_B);
        p = fma(p, z, -P_C);
        p = fma(p, z, P_D);
        p = fma(p, z, -P_E);
        p = fma(p, z, P_F);
        double pz = p * z;
        double q = fma(Q_A, z, -Q_B);
        q = fma(q, z, Q_C);
        q = fma(q, z, -Q_D);
        q = fma(q, z, Q_E);
        double r = pz / q;
        if (large == 0) {
            double w = fma(-x, r, PIO2_LO);
            return PIO2_HI - (x - w);
        }
        if (raw < 0) {
            double w = fma(r, s, -PIO2_LO);
            w = w + s;
            return PI - (w + w);
        }
        double df = bits(Double.doubleToRawLongBits(s) & 0xffffffff00000000L);
        double numerator = fma(-df, df, z);
        double c = numerator / (df + s);
        double twoC = c + c;
        double w = fma(r, s + s, twoC);
        return w + (df + df);
    }

    public static double atan2(double y, double x) {
        return CrtAtan2.atan2(y, x, FMA3);
    }

    public static double atan2Sse2(double y, double x) {
        return CrtAtan2.atan2(y, x, false);
    }

    public static double atan2Fma3(double y, double x) {
        return CrtAtan2.atan2(y, x, true);
    }

    public static float atan2f(float y, float x) {
        return CrtAtan2.atan2f(y, x, FMA3);
    }

    public static float atan2fSse2(float y, float x) {
        return CrtAtan2.atan2f(y, x, false);
    }

    public static float atan2fFma3(float y, float x) {
        return CrtAtan2.atan2f(y, x, true);
    }

    public static float powf(float x, float y) {
        return CrtPowf.powf(x, y, FMA_AVX);
    }

    public static float powfSse2(float x, float y) {
        return CrtPowf.powf(x, y, false);
    }

    public static float powfAvx(float x, float y) {
        return CrtPowf.powf(x, y, true);
    }

    public static float fmaf(float a, float b, float c) {
        if (Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(c) || Float.isInfinite(a) || Float.isInfinite(b)
                || Float.isInfinite(c)) {
            return a * b + c;
        }
        double product = (double) a * (double) b;
        BigDecimal exact = new BigDecimal(product).add(new BigDecimal(c));
        if (exact.signum() == 0) {
            boolean negativeProduct = (Double.doubleToRawLongBits(product) < 0) || (product == 0.0
                    && ((Float.floatToRawIntBits(a) ^ Float.floatToRawIntBits(b)) < 0));
            boolean negativeAddend = Float.floatToRawIntBits(c) < 0;
            return negativeProduct && negativeAddend && c == 0.0f ? -0.0f : 0.0f;
        }
        return exact.floatValue();
    }

    private static final float ACOSF_P0 = Float.intBitsToFloat(0x3b81ce6b);
    private static final float ACOSF_P1 = Float.intBitsToFloat(0x3d678bdd);
    private static final float ACOSF_P2 = Float.intBitsToFloat(0x3e3c94dc);
    private static final float ACOSF_P3 = Float.intBitsToFloat(0xbc5b3fe1);
    private static final float ACOSF_Q0 = Float.intBitsToFloat(0x3f561f0d);
    private static final float ACOSF_Q1 = Float.intBitsToFloat(0x3f8d6fa5);

    public static float acosf(float x) {
        return acosf(x, FMA3);
    }

    public static float acosf(float x, boolean fma3) {
        int bits = Float.floatToRawIntBits(x);
        int exponent = (bits >>> 23) & 0xff;
        if ((bits & 0x7fffffff) > 0x7f800000) {
            return Float.intBitsToFloat(bits | 0x400000);
        }
        if (exponent < 0x65) {
            return Float.intBitsToFloat(0x3fc90fdb);
        }
        if (exponent >= 0x7f) {
            if (x == 1.0f) {
                return 0.0f;
            }
            if (x == -1.0f) {
                return Float.intBitsToFloat(0x40490fdb);
            }
            return Float.intBitsToFloat(0xffc00000);
        }
        float magnitude = bits < 0 ? Float.intBitsToFloat(bits ^ 0x80000000) : x;
        boolean large = exponent == 0x7e;
        float z;
        float s = 0.0f;
        if (large) {
            z = (1.0f - magnitude) * 0.5f;
            s = (float) Math.sqrt(z);
        } else {
            z = magnitude * magnitude;
        }
        float r;
        if (fma3) {
            float p = fmaf(Float.intBitsToFloat(Float.floatToRawIntBits(ACOSF_P0) ^ 0x80000000), z, ACOSF_P3);
            p = fmaf(p, z, Float.intBitsToFloat(Float.floatToRawIntBits(ACOSF_P1) ^ 0x80000000));
            p = fmaf(p, z, ACOSF_P2);
            float numerator = p * z;
            float denominator = fmaf(Float.intBitsToFloat(Float.floatToRawIntBits(ACOSF_Q0) ^ 0x80000000), z, ACOSF_Q1);
            r = numerator / denominator;
        } else {
            float p = ACOSF_P3 - z * ACOSF_P0;
            float denominator = ACOSF_Q1 - z * ACOSF_Q0;
            p = p * z;
            p = p - ACOSF_P1;
            p = p * z;
            p = p + ACOSF_P2;
            p = p * z;
            r = p / denominator;
        }
        if (!large) {
            double tail = PIO2_LO - (double) (r * x);
            return (float) (PIO2_HI - ((double) x - tail));
        }
        if (bits < 0) {
            double sum = ((double) (s * r) - PIO2_LO) + (double) s;
            return (float) (PI - (sum + sum));
        }
        float df = Float.intBitsToFloat(Float.floatToRawIntBits(s) & 0xffff0000);
        if (fma3) {
            float sum = df + s;
            float c = fmaf(Float.intBitsToFloat(Float.floatToRawIntBits(df) ^ 0x80000000), df, z) / sum;
            c = c + c;
            float twice = s + s;
            float w = fmaf(r, twice, c);
            return w + (df + df);
        }
        float w = (s + s) * r;
        float square = df * df;
        float sum = df + s;
        float c = (z - square) / sum;
        c = c + c;
        w = w + c;
        return w + (df + df);
    }

    public static double hypot(double x, double y) {
        long ax = Double.doubleToRawLongBits(x) & ABS;
        long ay = Double.doubleToRawLongBits(y) & ABS;
        long ex = ax >>> 52;
        long ey = ay >>> 52;
        if (ex == 0x7ff) {
            if ((ax & MANTISSA) == 0) {
                return INFINITY;
            }
            if (ey == 0x7ff && (ay & MANTISSA) == 0) {
                return INFINITY;
            }
            return x * x + y * y;
        }
        if (ey == 0x7ff) {
            if ((ay & MANTISSA) == 0) {
                return INFINITY;
            }
            return x * x + y * y;
        }
        int difference = (int) ex - (int) ey;
        int scale = 0;
        double a = bits(ax);
        double b = bits(ay);
        if (ax == 0) {
            return b;
        }
        if (ay == 0) {
            return a;
        }
        if (Integer.compareUnsigned(difference + 0x36, 0x6c) > 0) {
            return a + b;
        }
        if (ex > 0x5f3 || ey > 0x5f3) {
            a = bits(ax + 0xda80000000000000L);
            b = bits(ay + 0xda80000000000000L);
            scale = 0x258;
        } else if (ex < 0x20b || ey < 0x20b) {
            double bias = bits(0xa590000000000000L);
            scale = -0x258;
            if (ex != 0) {
                a = bits(ax + 0x2580000000000000L);
            } else {
                a = bits(ax + 0x2590000000000000L) + bias;
            }
            if (ey != 0) {
                b = bits(ay + 0x2580000000000000L);
            } else {
                b = bits(ay + 0x2590000000000000L) + bias;
            }
        }
        double sum = a * a + b * b;
        double result = Math.sqrt(sum);
        if (scale != 0) {
            result = result * bits((long) (scale + 0x3ff) << 52);
        }
        if (result > bits(0x7fefffffffffffffL)) {
            return INFINITY;
        }
        return result;
    }
}
