package yt.ren.slate.util.math;

public class DoubleTrig {
    private static final int[] TWO_OVER_PI = {
            0xa2f983, 0x6e4e44, 0x1529fc, 0x2757d1, 0xf534dd, 0xc0db62, 0x95993c, 0x439041,
            0xfe5163, 0xabdebb, 0xc561b7, 0x246e3a, 0x424dd2, 0xe00649, 0x2eea09, 0xd1921c,
            0xfe1deb, 0x1cb129, 0xa73ee8, 0x8235f5, 0x2ebb44, 0x84e99c, 0x7026b4, 0x5f7e41,
            0x3991d6, 0x398353, 0x39f49c, 0x845f8b, 0xbdf928, 0x3b1ff8, 0x97ffde, 0x05980f,
            0xef2f11, 0x8b5a0a, 0x6d1f6d, 0x367ecf, 0x27cb09, 0xb74f46, 0x3f669e, 0x5fea2d,
            0x7527ba, 0xc7ebe5, 0xf17b3d, 0x0739f7, 0x8a5292, 0xea6bfb, 0x5fb11f, 0x8d5d08,
            0x560330, 0x46fc7b, 0x6babf0, 0xcfbc20, 0x9af436, 0x1da9e3, 0x91615e, 0xe61b08,
            0x659985, 0x5f14a0, 0x68408d, 0xffd880, 0x4d7327, 0x310606, 0x1556ca, 0x73a8c9,
            0x60e27b, 0xc08c6b,
    };
    private static final double[] PI_PARTS = {
            bits(0x3ff921fb40000000L), bits(0x3e74442d00000000L), bits(0x3cf8469880000000L), bits(0x3b78cc5160000000L),
            bits(0x39f01b8380000000L), bits(0x387a252040000000L), bits(0x36e3822280000000L), bits(0x3569f31d00000000L),
    };
    private static final int INITIAL_PRECISION_ONE = 4;
    private static final long[] LEADS = {0, 0x3ff921fb54400000L, 0x400921fb54400000L, 0x4012d97c7f300000L, 0x401921fb54400000L};
    private static final long[] TAILS = {0, 0x3dd0b4611a626331L, 0x3de0b4611a626331L, 0x3de90e91a79394caL, 0x3df0b4611a626331L};

    private static double bits(long value) {
        return Double.longBitsToDouble(value);
    }

    private static double flip(double value) {
        return bits(Double.doubleToRawLongBits(value) ^ 0x8000000000000000L);
    }

    private static int integer(double value) {
        return Sse.cvttsd2si(value);
    }

    private static int exponent(double value) {
        return (int) ((Double.doubleToRawLongBits(value) >>> 52) & 0x7ff);
    }

    private static double scalePower(double value, int power) {
        return value * bits((long) (1023 + power) << 52);
    }

    private static double convolve(double[] input, int count, int first, int i) {
        double sum = 0;
        for (int j = 0; j < count; ++j) {
            int index = first + i - j;
            double factor = index < 0 ? 0.0 : (double) TWO_OVER_PI[index];
            sum = sum + input[j] * factor;
        }
        return sum;
    }

    private static int reduceLargeDouble(double[] input, int count, int power, double[] out) {
        final int initial = INITIAL_PRECISION_ONE;
        int first = power > 3 ? (power - 3) / 24 : 0;
        int q0 = power - 24 * (first + 1);
        int last = initial;
        double[] q = new double[20];
        double[] terms = new double[20];
        int[] digits = new int[20];
        for (int i = 0; i <= last; ++i) {
            q[i] = convolve(input, count, first, i);
        }
        int quadrant;
        int complement;
        for (; ; ) {
            double z = q[last];
            for (int i = 0, j = last; j > 0; ++i, --j) {
                double carry = integer(z * 0x1p-24);
                digits[i] = integer(z - carry * 0x1p24);
                z = q[j - 1] + carry;
            }
            z = scalePower(z, q0);
            z = ScalarTrig.floorGameDouble(z * 0.125) * -8.0 + z;
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
                for (int i = 0; i < last; ++i) {
                    if (carry) {
                        digits[i] = 0xffffff - digits[i];
                    } else if (digits[i] != 0) {
                        carry = true;
                        digits[i] = 0x1000000 - digits[i];
                    }
                }
                if (q0 > 0) {
                    digits[last - 1] &= q0 == 1 ? 0x7fffff : 0x3fffff;
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
                for (int i = last - 1; i >= initial; --i) {
                    nonzero |= digits[i];
                }
                if (nonzero == 0) {
                    int extra = 1;
                    while (digits[initial - extra] == 0) {
                        ++extra;
                    }
                    for (int i = last + 1; i <= last + extra; ++i) {
                        q[i] = convolve(input, count, first, i);
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
                if (z >= 0x1p24) {
                    double carry = integer(z * 0x1p-24);
                    digits[last] = integer(carry * -0x1p24 + z);
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
        for (int i = last; i >= 0; --i) {
            q[i] = (double) digits[i] * factor;
            factor = factor * 0x1p-24;
        }
        for (int i = last; i >= 0; --i) {
            int end = Math.min(last - i, initial);
            int k = 0;
            double sum = 0;

            if (end >= 3) {
                for (; k + 3 <= end; k += 4) {
                    for (int j = 0; j < 4; ++j) {
                        sum = PI_PARTS[k + j] * q[i + k + j] + sum;
                    }
                }
            }
            for (; k <= end; ++k) {
                sum = sum + PI_PARTS[k] * q[i + k];
            }
            terms[last - i] = sum;
        }
        double sum = 0;
        for (int i = last; i >= 0; --i) {
            sum = sum + terms[i];
        }
        out[0] = complement != 0 ? flip(sum) : sum;
        double tail = terms[0] - sum;
        for (int i = 1; i <= last; ++i) {
            tail = tail + terms[i];
        }
        out[1] = complement != 0 ? flip(tail) : tail;
        return quadrant & 7;
    }

    private static int reduceDouble(double value, double[] out) {
        long raw = Double.doubleToRawLongBits(value);
        int high = (int) (raw >>> 32) & 0x7fffffff;
        boolean negative = raw < 0;
        boolean special = false;
        int quick = 0;
        if (Integer.compareUnsigned(high, 0x400f6a7a) <= 0) {
            if ((high & 0xfffff) != 0x921fb) {
                quick = Integer.compareUnsigned(high, 0x4002d97c) <= 0 ? 1 : 2;
            }
        } else if (Integer.compareUnsigned(high, 0x401c463c) < 0) {
            if (Integer.compareUnsigned(high, 0x4015fdbd) < 0) {
                if (high != 0x4012d97c) {
                    quick = 3;
                } else {
                    special = true;
                }
            } else {
                if (high != 0x401921fb) {
                    quick = 4;
                } else {
                    special = true;
                }
            }
        } else if (Integer.compareUnsigned(high, 0x413921fb) >= 0) {
            if (Integer.compareUnsigned(high, 0x7ff00000) >= 0) {
                out[0] = 0.0;
                out[1] = out[0];
                return 0;
            }
            double remainder = bits((raw & 0xfffffffffffffL) | 0x4160000000000000L);
            double[] chunks = new double[3];
            chunks[0] = integer(remainder);
            remainder = (remainder - chunks[0]) * 0x1p24;
            chunks[1] = integer(remainder);
            chunks[2] = (remainder - chunks[1]) * 0x1p24;
            int count = 3;
            while (chunks[count - 1] == 0.0) {
                --count;
            }
            int quadrant = reduceLargeDouble(chunks, count, (high >>> 20) - 0x416, out);
            if (negative) {
                out[0] = flip(out[0]);
                out[1] = flip(out[1]);
                return -quadrant;
            }
            return quadrant;
        }
        if (quick != 0) {
            long sign = negative ? 0L : 0x8000000000000000L;
            double lead = value + bits(LEADS[quick] ^ sign);
            double tail = bits(TAILS[quick] ^ sign);
            out[0] = lead + tail;
            out[1] = (lead - out[0]) + tail;
            return negative ? -quick : quick;
        }
        double count = bits(0x3fe45f306dc9c883L) * value + 6755399441055744.0 - 6755399441055744.0;
        int quadrant = integer(count);
        double remainder = bits(0xbff921fb54400000L) * count + value;
        double tail = bits(0x3dd0b4611a626331L) * count;
        double trial = remainder - tail;
        boolean adjusted = false;
        if (trial < bits(0xbfe921fb54442d18L)) {
            --quadrant;
            count = count - 1.0;
            adjusted = true;
        } else if (trial > bits(0x3fe921fb54442d18L)) {
            ++quadrant;
            count = count + 1.0;
            adjusted = true;
        }
        if (adjusted) {
            remainder = value + bits(0xbff921fb54400000L) * count;
            tail = bits(0x3dd0b4611a626331L) * count;
        }
        out[0] = remainder - tail;
        int originalExponent = high >>> 20;
        if (special ? exponent(out[0]) < 0x3f1 : originalExponent - exponent(out[0]) > 16) {
            double part = bits(0x3dd0b4611a600000L) * count;
            double second = remainder - part;
            tail = bits(0x3ba3198a2e037073L) * count - ((remainder - second) - part);
            remainder = second;
            out[0] = remainder - tail;
            boolean third = special ? ((Double.doubleToRawLongBits(out[0]) >>> 56) & 0x7f) <= 0x3c
                    : originalExponent - exponent(out[0]) >= 50;
            if (third) {
                double part3 = bits(0x3ba3198a2e000000L) * count;
                double thirdRemainder = remainder - part3;
                tail = count * bits(0x397b839a252049c1L) - ((remainder - thirdRemainder) - part3);
                remainder = thirdRemainder;
                out[0] = remainder - tail;
            }
        }
        out[1] = (remainder - out[0]) - tail;
        return quadrant;
    }

    private static double sineSmall(double value) {
        double square = value * value;
        double sixth = square * square * square;
        double high = sixth * (square * bits(0x3de5d93a5acfd57cL) + bits(0xbe5ae5e68a2b9cebL));
        double low = square * (square * bits(0x3ec71de357b1fe7dL) + bits(0xbf2a01a019c161d5L));
        double polynomial = ((low + bits(0x3f8111111110f8a6L)) + high) * square + bits(0xbfc5555555555549L);
        return value + polynomial * (value * square);
    }

    private static double sineCorrected(double valueHigh, double valueLow) {
        double square = valueHigh * valueHigh;
        double fourth = square * square;
        double high = bits(0x3de5d93a5acfd57cL) * square + bits(0xbe5ae5e68a2b9cebL);
        double low = (bits(0x3ec71de357b1fe7dL) * square + bits(0xbf2a01a019c161d5L)) * square
                + bits(0x3f8111111110f8a6L);
        double polynomial = low + fourth * square * high;
        double cube = valueHigh * square;
        double correction = (0.5 * valueLow - polynomial * cube) * square - valueLow;
        return valueHigh - (cube * bits(0x3fc5555555555549L) + correction);
    }

    private static double cosineCorrected(double valueHigh, double valueLow) {
        double square = valueHigh * valueHigh;
        double fourth = square * square;
        double low = (bits(0xbf56c16c16c15177L) + bits(0x3efa01a019cb1590L) * square) * square
                + bits(0x3fa555555555554cL);
        double high = (bits(0x3e21ee9ebdb4b1c4L) - bits(0x3da8fae9be8838d4L) * square) * square
                + bits(0xbe927e4f809c52adL);
        double polynomial = fourth * fourth * high + square * low;
        double half = 0.5 * square;
        double leading = 1.0 - half;
        double roundoff = (1.0 - leading) - half;
        return ((polynomial * square - valueHigh * valueLow) + roundoff) + leading;
    }

    public static double sine(double value) {
        int high = (int) (Double.doubleToRawLongBits(value) >>> 32) & 0x7fffffff;
        if (Integer.compareUnsigned(high, 0x3fe921fc) < 0) {
            if (Integer.compareUnsigned(high, 0x3e500000) < 0) {
                return value;
            }
            return sineSmall(value);
        }
        if (Integer.compareUnsigned(high, 0x7ff00000) >= 0) {
            return 0.0;
        }
        double[] reduced = new double[2];
        int quadrant = reduceDouble(value, reduced) & 3;
        double result = (quadrant & 1) != 0 ? cosineCorrected(reduced[0], reduced[1])
                : sineCorrected(reduced[0], reduced[1]);
        return (quadrant & 2) != 0 ? flip(result) : result;
    }
}
