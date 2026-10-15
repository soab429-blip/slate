package yt.ren.slate.feature.combat;

import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.MouseOverEvent;
import yt.ren.slate.game.MouseOver;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;

public class Reach extends Module {
    private final ScalarSetting distance = register("distance", new ScalarSetting("Distance", "%0.2f blocks", 0.01f, 0.0f, 3.0f, 6.0f, 3.05f, 0.0f));
    private final ScalarSetting activateTime = register("activate_time", new ScalarSetting("Activate for", "%.0f tick(s)", 1.0f, 0.0f, 1.0f, 10.0f, 3.0f, 0.0f));
    private final BooleanSetting requireSprint = register("require_sprint", new BooleanSetting("Sprinting", 0, 0));

    private int previousAttack;
    private int delay;

    public Reach(ClientRuntime runtime) {
        super(ModuleId.REACH, "Reach", "Increases your melee range.", Category.COMBAT);
    }

    @Override
    protected void setup() {
        on(EventTypes.MOUSE_OVER, this::extend);
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.2f", (double) distance.get());
    }

    private void extend(MouseOverEvent event) {
        if (delay > 0) {
            --delay;
        }
        ClientGameContext c = event.game();
        if (requireSprint.get() && !c.player.isSprinting()) {
            return;
        }
        if (delay == 0 && c.player.lastAttackTime != previousAttack) {
            previousAttack = c.player.lastAttackTime;
            delay = 9;
        }
        if (activateTime.get() > (float) delay) {
            MouseOver.requestCustomMouseOver(event);
            event.reach = distance.get();
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(distance);
        w.heading("Advanced");
        w.show(activateTime);
        w.heading("Conditions");
        w.show(requireSprint);
    }
}
