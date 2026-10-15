package yt.ren.slate.setting;

public class ColorSetting extends Setting {
    private final float[] channels = new float[3];
    private final float[] snapshot = new float[3];
    private final float[] defaultValue = new float[3];

    public ColorSetting(String label, float r, float g, float b, float sr, float sg, float sb) {
        super(label);
        channels[0] = r;
        channels[1] = g;
        channels[2] = b;
        snapshot[0] = sr;
        snapshot[1] = sg;
        snapshot[2] = sb;
        System.arraycopy(channels, 0, defaultValue, 0, 3);
    }

    public float red() {
        return channels[0];
    }

    public float green() {
        return channels[1];
    }

    public float blue() {
        return channels[2];
    }

    public float channel(int index) {
        return channels[index];
    }

    public float[] rgbArray() {
        return channels.clone();
    }

    public float[] liveChannels() {
        return channels;
    }

    public int rgb() {
        return pack(channels[0]) << 16 | pack(channels[1]) << 8 | pack(channels[2]);
    }

    public void set(float red, float green, float blue) {
        channels[0] = red;
        channels[1] = green;
        channels[2] = blue;
    }

    public float[] snapshot() {
        return snapshot.clone();
    }

    private static int pack(float component) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, component)) * 255.0f);
    }

    @Override
    public void reset() {
        System.arraycopy(defaultValue, 0, channels, 0, 3);
    }

    @Override
    public void captureSnapshot() {
        System.arraycopy(channels, 0, snapshot, 0, 3);
    }

    @Override
    public void restoreSnapshot() {
        System.arraycopy(snapshot, 0, channels, 0, 3);
    }
}
