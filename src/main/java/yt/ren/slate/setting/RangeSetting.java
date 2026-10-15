package yt.ren.slate.setting;

public class RangeSetting extends Setting {
    private final String singleFormat;
    private final String rangeFormat;
    private final float step;
    private final float displayScale;
    private final float minimum;
    private final float maximum;
    private final float defaultLow;
    private final float defaultHigh;
    private float low;
    private float high;
    private float snapshotLow;
    private float snapshotHigh;

    public RangeSetting(String label, String singleFormat, String rangeFormat, float step, float displayScale,
                        float minimum, float maximum, float low, float high, float snapshotLow, float snapshotHigh) {
        super(label);
        this.singleFormat = singleFormat;
        this.rangeFormat = rangeFormat;
        this.step = step;
        this.displayScale = displayScale;
        this.minimum = minimum;
        this.maximum = maximum;
        this.low = low;
        this.high = high;
        this.snapshotLow = snapshotLow;
        this.snapshotHigh = snapshotHigh;
        this.defaultLow = low;
        this.defaultHigh = high;
    }

    public float low() {
        return low;
    }

    public float high() {
        return high;
    }

    public void setLow(float value) {
        low = value;
    }

    public void setHigh(float value) {
        high = value;
    }

    public void set(float lowValue, float highValue) {
        low = lowValue;
        high = highValue;
    }

    public float lerp(float amount) {
        return low + (high - low) * amount;
    }

    public float snapshotLow() {
        return snapshotLow;
    }

    public float snapshotHigh() {
        return snapshotHigh;
    }

    public String singleFormat() {
        return singleFormat;
    }

    public String rangeFormat() {
        return rangeFormat;
    }

    public float step() {
        return step;
    }

    public float displayScale() {
        return displayScale;
    }

    public float minimum() {
        return minimum;
    }

    public float maximum() {
        return maximum;
    }

    @Override
    public void reset() {
        low = defaultLow;
        high = defaultHigh;
    }

    @Override
    public void captureSnapshot() {
        snapshotLow = low;
        snapshotHigh = high;
    }

    @Override
    public void restoreSnapshot() {
        low = snapshotLow;
        high = snapshotHigh;
    }
}
