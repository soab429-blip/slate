package yt.ren.slate.ui;

import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;

public abstract class SettingsWidgets {
    public static int dirty;

    public abstract void heading(String label);

    protected abstract boolean booleanWidget(BooleanSetting setting);

    protected abstract boolean scalarWidget(ScalarSetting setting);

    protected abstract boolean rangeWidget(RangeSetting setting);

    protected abstract boolean choiceWidget(ChoiceSetting setting);

    protected abstract boolean colorWidget(ColorSetting setting);

    protected abstract boolean listWidget(ListSetting setting, String[] suggestions);

    public final void show(BooleanSetting s) {
        if (booleanWidget(s)) {
            dirty = 1;
        }
    }

    public final void show(ScalarSetting s) {
        if (scalarWidget(s)) {
            dirty = 1;
        }
    }

    public final void show(RangeSetting s) {
        if (rangeWidget(s)) {
            dirty = 1;
        }
    }

    public final void show(ChoiceSetting s) {
        if (choiceWidget(s)) {
            dirty = 1;
        }
    }

    public final void show(ColorSetting s) {
        if (colorWidget(s)) {
            dirty = 1;
        }
    }

    public final void show(ListSetting s) {
        String[] names = s.storage() != null ? s.storage().suggestions() : null;
        if (listWidget(s, names)) {
            s.markModified();
            dirty = 1;
        }
    }
}
