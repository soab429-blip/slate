package yt.ren.slate.setting;

public class ListSetting extends Setting {
    private final String inputLabel;
    private final int elementKind;
    private ListStorage storage;
    private boolean modified;

    public ListSetting(String label, String inputLabel, int elementKind) {
        super(label);
        this.inputLabel = inputLabel;
        this.elementKind = elementKind;
    }

    public void attachStorage(ListStorage value) {
        storage = value;
    }

    public ListStorage storage() {
        return storage;
    }

    public String inputLabel() {
        return inputLabel;
    }

    public int elementKind() {
        return elementKind;
    }

    public boolean modified() {
        return modified;
    }

    public void markModified() {
        modified = true;
    }

    public void clearModified() {
        modified = false;
    }

    public long size() {
        return storage != null ? storage.count() : 0;
    }

    public String name(long index) {
        return storage.name(index);
    }

    public boolean add(String name, float[] color) {
        boolean added = storage != null && storage.insert(name, color);
        if (added) {
            modified = true;
        }
        return added;
    }

    public void remove(long index) {
        if (storage != null) {
            storage.remove(index);
            modified = true;
        }
    }

    @Override
    public void reset() {
        if (storage != null) {
            storage.clear();
            modified = true;
        }
    }

    @Override
    public void captureSnapshot() {
    }

    @Override
    public void restoreSnapshot() {
    }
}
