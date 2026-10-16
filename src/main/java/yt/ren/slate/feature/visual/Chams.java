package yt.ren.slate.feature.visual;

import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class Chams extends Module {
    private final BooleanSetting hideFriendlies = register("hide_friendlies", new BooleanSetting("Hide friendlies", 0, 0));

    public Chams(ClientRuntime runtime) {
        super(ModuleId.CHAMS, "Chams", "Renders players behind blocks.", Category.VISUAL);
    }

    public boolean hidesFriendlies() {
        return hideFriendlies.get();
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(hideFriendlies);
    }
}
