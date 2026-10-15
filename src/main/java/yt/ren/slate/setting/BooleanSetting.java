package yt.ren.slate.setting;

public class BooleanSetting extends Setting {
    private final boolean defaultValue;
    private boolean value;
    private boolean snapshot;

    public BooleanSetting(String label, int value, int snapshot) {
        super(label);
        this.value = value != 0;
        this.snapshot = snapshot != 0;
        this.defaultValue = value != 0;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean enabled) {
        value = enabled;
    }

    public void toggle() {
        value = !value;
    }

    public boolean snapshot() {
        return snapshot;
    }

    public boolean defaultValue() {
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
