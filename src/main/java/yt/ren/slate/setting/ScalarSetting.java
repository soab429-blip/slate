package yt.ren.slate.setting;

public class ScalarSetting extends Setting {
    private final String format;
    private final float step;
    private final float displayScale;
    private final float minimum;
    private final float maximum;
    private final float defaultValue;
    private float value;
    private float snapshot;

    public ScalarSetting(String label, String format, float step, float displayScale, float minimum,
                         float maximum, float value, float snapshot) {
        super(label);
        this.format = format;
        this.step = step;
        this.displayScale = displayScale;
        this.minimum = minimum;
        this.maximum = maximum;
        this.value = value;
        this.snapshot = snapshot;
        this.defaultValue = value;
    }

    public float get() {
        return value;
    }

    public void set(float next) {
        value = next;
    }

    public float snapshot() {
        return snapshot;
    }

    public String format() {
        return format;
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

    public float defaultValue() {
        return defaultValue;
    }

    @Override
    public void reset() {
        value = defaultValue;
    }

    @Override
    public void captureSnapshot() {
        snapshot = value;
    }

    @Override
    public void restoreSnapshot() {
        value = snapshot;
    }
}
