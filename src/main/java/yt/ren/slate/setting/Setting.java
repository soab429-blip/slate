package yt.ren.slate.setting;

import yt.ren.slate.module.ModuleId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public abstract class Setting {
    private final String label;
    private final ByteBuffer uiState = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    private final Object valueKey = new Object();
    private final Object upperKey = new Object();
    private ModuleId module;
    private String key;

    protected Setting(String label) {
        this.label = label;
    }

    public void attach(ModuleId owner, String name) {
        if (key != null) {
            throw new IllegalStateException("Setting already attached: " + this);
        }
        this.module = owner;
        this.key = name;
    }

    public String key() {
        return key;
    }

    public ModuleId module() {
        return module;
    }

    public String label() {
        return label;
    }

    public ByteBuffer uiState() {
        return uiState;
    }

    public Object valueKey() {
        return valueKey;
    }

    public Object upperKey() {
        return upperKey;
    }

    public abstract void reset();

    public abstract void captureSnapshot();

    public abstract void restoreSnapshot();

    @Override
    public String toString() {
        return key != null ? module + "." + key : label;
    }
}
