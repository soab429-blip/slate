package yt.ren.slate.aim;

public class AimRandom {
    private final int[] s = new int[4];

    public AimRandom(int w0, int w1, int w2, int w3) {
        s[0] = w0;
        s[1] = w1;
        s[2] = w2;
        s[3] = w3;
    }

    public AimRandom() {
    }

    public void seed(int w0, int w1, int w2, int w3) {
        s[0] = w0;
        s[1] = w1;
        s[2] = w2;
        s[3] = w3;
    }

    public float nextRange(float minimum, float maximum) {
        float width = maximum - minimum;
        int output = s[3] + s[0];
        int mixed = s[2] ^ s[0];
        int cross = s[1] ^ s[3];
        int shifted = s[1] << 9;
        s[0] ^= cross;
        s[2] = mixed ^ shifted;
        s[3] = Integer.rotateLeft(cross, 11);
        s[1] ^= mixed;
        return (float) (output >>> 8) * 0x1.0p-24f * width + minimum;
    }

    public float nextUnit() {
        return nextRange(0.0f, 1.0f);
    }

    public boolean below(float threshold) {
        return nextUnit() < threshold;
    }

    public float range(float minimum, float maximum) {
        return nextRange(minimum, maximum);
    }
}
