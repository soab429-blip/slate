package yt.ren.slate.util.math;

public class ScalarTrig {
    private static double bits(long value) {
        return Double.longBitsToDouble(value);
    }

    private static final double HALF_PI = bits(0x3ff921fb54442d18L);
    private static final double PI = bits(0x400921fb54442d18L);
    private static final double THREE_HALF_PI = bits(0x4012d97c7f3321d2L);
    private static final double TWO_PI = bits(0x401921fb54442d18L);

    private static double negate(double value) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(value) ^ 0x8000000000000000L);
    }

    private static float negate(float value) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(value) ^ 0x80000000);
    }

    private static float narrow(double value) {
        return (float) value;
    }

    private static int integer(double value) {
        return (int) value;
    }

    public static double floorGameDouble(double value) {
        long raw = Double.doubleToRawLongBits(value);
        long exponent = (raw >> 52) & 2047;
        if (value == 0.0 || exponent >= 1075) {
            return value;
        }
        boolean negative = (raw >>> 63) != 0;
        double rounded = negative
                ? (value - 4503599627370496.0) + 4503599627370496.0
                : (value + 4503599627370496.0) - 4503599627370496.0;
        if (exponent < 1023) {
            return negative ? -1.0 : 0.0;
        }
        double difference = rounded - value;
        double result = value + difference;
        return difference > 0.0 ? result - 1.0 : result;
    }

    private static double scalePower(double value, int exponent) {
        return value * bits((long) (1023 + exponent) << 52);
    }

    private static final int[] TWO_OVER_PI = {
            0xa2f983, 0x6e4e44, 0x1529fc, 0x2757d1, 0xf534dd, 0xc0db62, 0x95993c, 0x439041,
            0xfe5163, 0xabdebb, 0xc561b7, 0x246e3a, 0x424dd2, 0xe00649, 0x2eea09, 0xd1921c,
            0xfe1deb, 0x1cb129, 0xa73ee8, 0x8235f5, 0x2ebb44, 0x84e99c, 0x7026b4, 0x5f7e41,
            0x3991d6, 0x398353, 0x39f49c, 0x845f8b, 0xbdf928, 0x3b1ff8, 0x97ffde, 0x05980f
    };

    private static final double[] PI_PARTS = {
            bits(0x3ff921fb40000000L), bits(0x3e74442d00000000L),
            bits(0x3cf8469880000000L), bits(0x3b78cc5160000000L)
    };

    private static final double TWO_POW_24 = 0x1p24;
    private static final double TWO_POW_NEG_24 = 0x1p-24;

    private static int reduceLargeFloat(double input, int exponent, double[] result) {
        final int initial = 3;
        int first = (exponent - 3) / 24;
        int q0 = exponent - 24 * (first + 1);
        int last = initial;
        double[] q = new double[20];
        double[] terms = new double[20];
        int[] digits = new int[20];
        for (int i = 0; i <= last; i++) {
            q[i] = 0.0 + input * (double) TWO_OVER_PI[first + i];
        }
        int quadrant;
        int complement;
        for (; ; ) {
            double z = q[last];
            for (int i = 0, j = last; j > 0; i++, j--) {
                double carry = integer(z * TWO_POW_NEG_24);
                digits[i] = integer(z - carry * TWO_POW_24);
                z = q[j - 1] + carry;
            }
            z = scalePower(z, q0);
            z = floorGameDouble(z * 0.125) * -8.0 + z;
            quadrant = integer(z);
            z = z - (double) integer(z);
            complement = 0;
            if (q0 > 0) {
                int carry = digits[last - 1] >>> (24 - q0);
                quadrant += carry;
                digits[last - 1] -= carry << (24 - q0);
                complement = digits[last - 1] >>> (23 - q0);
            } else if (q0 == 0) {
                complement = digits[last - 1] >>> 23;
            } else if (z >= 0.5) {
                complement = 2;
            }
            if (complement > 0) {
                ++quadrant;
                boolean carry = false;
                for (int i = 0; i < last; i++) {
                    if (carry) {
                        digits[i] = 0xffffff - digits[i];
                    } else if (digits[i] != 0) {
                        carry = true;
                        digits[i] = 0x1000000 - digits[i];
                    }
                }
                if (q0 > 0) {
                    digits[last - 1] &= (q0 == 1) ? 0x7fffff : 0x3fffff;
                }
                if (complement == 2) {
                    z = 1.0 - z;
                    if (carry) {
                        z = z - scalePower(1.0, q0);
                    }
                }
            }
            if (z == 0.0) {
                int nonzero = 0;
                for (int i = last - 1; i >= initial; i--) {
                    nonzero |= digits[i];
                }
                if (nonzero == 0) {
                    int extra = 1;
                    while (digits[initial - extra] == 0) {
                        ++extra;
                    }
                    for (int i = last + 1; i <= last + extra; i++) {
                        q[i] = 0.0 + input * (double) TWO_OVER_PI[first + i];
                    }
                    last += extra;
                    continue;
                }
                do {
                    --last;
                    q0 -= 24;
                } while (digits[last] == 0);
            } else {
                z = scalePower(z, -q0);
                if (z >= TWO_POW_24) {
                    double carry = integer(z * TWO_POW_NEG_24);
                    digits[last] = integer(carry * -TWO_POW_24 + z);
                    ++last;
                    q0 += 24;
                    digits[last] = integer(carry);
                } else {
                    digits[last] = integer(z);
                }
            }
            break;
        }
        double factor = scalePower(1.0, q0);
        for (int i = last; i >= 0; i--) {
            q[i] = (double) digits[i] * factor;
            factor = factor * TWO_POW_NEG_24;
        }
        for (int i = last; i >= 0; i--) {
            double sum = 0.0;
            for (int k = 0; k <= initial && k <= last - i; k++) {
                sum = PI_PARTS[k] * q[i + k] + sum;
            }
            terms[last - i] = sum;
        }
        double sum = 0.0;
        for (int i = last; i >= 0; i--) {
            sum = sum + terms[i];
        }
        result[0] = (complement != 0) ? negate(sum) : sum;
        return quadrant & 7;
    }

    private static int reduceFloat(float value, double[] result) {
        int raw = Float.floatToRawIntBits(value);
        int absolute = raw & 0x7fffffff;
        if (Integer.compareUnsigned(absolute, 0x4dc90fda) <= 0) {
            double count = (bits(0x3fe45f306dc9c883L) * (double) value + 6755399441055744.0) - 6755399441055744.0;
            int quadrant = integer(count);
            result[0] = bits(0xbe5110b4611a6263L) * count
                    + (bits(0xbff921fb50000000L) * count + (double) value);
            if (result[0] < bits(0xbfe921fb60000000L)) {
                count = count - 1.0;
                --quadrant;
            } else if (result[0] > bits(0x3fe921fb60000000L)) {
                count = count + 1.0;
                ++quadrant;
            } else {
                return quadrant;
            }
            result[0] = count * bits(0xbe5110b4611a6263L)
                    + ((double) value + bits(0xbff921fb50000000L) * count);
            return quadrant;
        }
        int exponent = (absolute >>> 23) - 150;
        float normalized = Float.intBitsToFloat(absolute - (exponent << 23));
        int quadrant = reduceLargeFloat(normalized, exponent, result);
        if ((raw >>> 31) != 0) {
            result[0] = negate(result[0]);
            return -quadrant;
        }
        return quadrant;
    }

    private static float sinePolynomial(double value, boolean negative, boolean firstBranch) {
        double square = value * value;
        double fourth = square * square;
        double cube = negative ? negate(value) * square : (square * value);
        double tail = square * bits(0x3ec6cd878c3b46a7L) + bits(0xbf2a00f9e2cae774L);
        double lead = square * bits(0x3f811110896efbb2L) + bits(0xbfc5555554cbac77L);
        double higher = fourth * cube * tail;
        double lower = cube * lead;
        return narrow((negative ? lower - value : lower + value) + higher);
    }

    private static float cosinePolynomial(double value, boolean negative) {
        double square = value * value;
        double fourth = square * square;
        double tail = bits(0x3ef99342e0ee5069L) * square + bits(0xbf56c087e80f1e27L);
        double lead = bits(0xbfdffffffd0c5e81L) * square + 1.0;
        double middle = fourth * bits(0x3fa55553e1053a42L) + lead;
        float answer = narrow(square * fourth * tail + middle);
        return negative ? negate(answer) : answer;
    }

    private static float exceptional(float value) {
        return 0.0f;
    }

    public static float sine(float value) {
        int raw = Float.floatToRawIntBits(value);
        int absolute = raw & 0x7fffffff;
        boolean negative = (raw >>> 31) != 0;
        if (Integer.compareUnsigned(absolute, 0x3f490fda) <= 0) {
            if (Integer.compareUnsigned(absolute, 0x39800000) < 0) {
                return value;
            }
            return sinePolynomial(value, false, true);
        }
        if (Integer.compareUnsigned(absolute, 0x407b53d2) < 0) {
            if (Integer.compareUnsigned(absolute, 0x4016cbe4) >= 0) {
                return sinePolynomial((double) value + (negative ? PI : -PI), true, false);
            }
            return cosinePolynomial((double) value + (negative ? HALF_PI : -HALF_PI), negative);
        }
        if (Integer.compareUnsigned(absolute, 0x40e231d6) < 0) {
            if (Integer.compareUnsigned(absolute, 0x40afede0) < 0) {
                return cosinePolynomial((double) value + (negative ? THREE_HALF_PI : -THREE_HALF_PI), !negative);
            }
            return sinePolynomial((double) value + (negative ? TWO_PI : -TWO_PI), false, false);
        }
        if (Integer.compareUnsigned(absolute, 0x7f7fffff) > 0) {
            return exceptional(value);
        }
        double[] reduced = new double[1];
        int quadrant = reduceFloat(value, reduced) & 3;
        if (quadrant == 1) {
            return cosinePolynomial(reduced[0], false);
        }
        if (quadrant == 2) {
            return sinePolynomial(reduced[0], true, false);
        }
        if (quadrant == 3) {
            return cosinePolynomial(reduced[0], true);
        }
        return sinePolynomial(reduced[0], false, false);
    }

    public static float cosine(float value) {
        int raw = Float.floatToRawIntBits(value);
        int absolute = raw & 0x7fffffff;
        boolean negative = (raw >>> 31) != 0;
        if (Integer.compareUnsigned(absolute, 0x3f490fda) <= 0) {
            if (Integer.compareUnsigned(absolute, 0x39800000) < 0) {
                return 1.0f;
            }
            return cosinePolynomial(value, false);
        }
        if (Integer.compareUnsigned(absolute, 0x407b53d2) < 0) {
            if (Integer.compareUnsigned(absolute, 0x4016cbe3) > 0) {
                return cosinePolynomial((double) value + (negative ? PI : -PI), true);
            }
            return sinePolynomial(negative ? (double) value + HALF_PI : HALF_PI - (double) value, false, false);
        }
        if (Integer.compareUnsigned(absolute, 0x40e231d6) < 0) {
            if (Integer.compareUnsigned(absolute, 0x40afeddf) <= 0) {
                return sinePolynomial(negative ? -THREE_HALF_PI - (double) value : (double) value - THREE_HALF_PI, false, false);
            }
            return cosinePolynomial((double) value + (negative ? TWO_PI : -TWO_PI), false);
        }
        if (Integer.compareUnsigned(absolute, 0x7f7fffff) > 0) {
            return exceptional(value);
        }
        double[] reduced = new double[1];
        int quadrant = reduceFloat(value, reduced) & 3;
        if (quadrant == 1) {
            return sinePolynomial(reduced[0], true, false);
        }
        if (quadrant == 2) {
            return cosinePolynomial(reduced[0], true);
        }
        if (quadrant == 3) {
            return sinePolynomial(reduced[0], false, false);
        }
        return cosinePolynomial(reduced[0], false);
    }
}
