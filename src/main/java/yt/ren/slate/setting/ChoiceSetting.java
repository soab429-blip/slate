package yt.ren.slate.setting;

public class ChoiceSetting extends Setting {
    private final int count;
    private final String[] choices;
    private final int uiFlag;
    private final int defaultValue;
    private int value;
    private int snapshot;

    public ChoiceSetting(String label, int count, int reserved, String[] choices, int value, int snapshot, int uiFlag) {
        super(label);
        this.count = count;
        this.choices = choices;
        this.value = value;
        this.snapshot = snapshot;
        this.uiFlag = uiFlag;
        this.defaultValue = value;
    }

    public int index() {
        return value;
    }

    public boolean is(int index) {
        return value == index;
    }

    public String selected() {
        return value >= 0 && value < choices.length ? choices[value] : "";
    }

    public String choice(int index) {
        return choices[index];
    }

    public int count() {
        return count;
    }

    public int uiFlag() {
        return uiFlag;
    }

    public void select(int index) {
        value = index;
    }

    public void select(String choice) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].equalsIgnoreCase(choice)) {
                value = i;
                return;
            }
        }
    }

    public String[] choices() {
        return choices.clone();
    }

    public int snapshot() {
        return snapshot;
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
