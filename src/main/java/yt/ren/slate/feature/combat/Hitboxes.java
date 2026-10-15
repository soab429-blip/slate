package yt.ren.slate.feature.combat;

import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.MouseOver;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;

public class Hitboxes extends Module {
    private final ScalarSetting expand = register("expand", new ScalarSetting("Expand", "%0.2f blocks", 0.01f, 0.0f, 0.0f, 1.0f, 0.1f, 0.0f));

    public Hitboxes(ClientRuntime runtime) {
        super(ModuleId.HITBOXES, "Hitboxes", "Expands hitboxes of other players.", Category.COMBAT);
    }

    @Override
    protected void setup() {
        on(EventTypes.MOUSE_OVER, e -> {
            MouseOver.requestCustomMouseOver(e);
            e.expand = expand.get();
        });
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.2f", (double) expand.get());
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(expand);
    }
}
