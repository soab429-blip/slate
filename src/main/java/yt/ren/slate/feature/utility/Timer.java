package yt.ren.slate.feature.utility;

import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.Controls;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;

public class Timer extends Module {
    private final ScalarSetting speed = register("speed", new ScalarSetting("Speed", "%0.2fx", 0.01f, 0.0f, 0.0f, 2.0f, 1.0f, 0.0f));
    private final BooleanSetting requireMouse = register("require_mouse", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireDamage = register("require_damage", new BooleanSetting("Damage", 0, 0));
    private final BooleanSetting requireFalling = register("require_falling", new BooleanSetting("Falling", 0, 0));

    public Timer(ClientRuntime runtime) {
        super(ModuleId.TIMER, "Timer", "Modifies the speed the game runs at.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.FRAME, e -> update(e.game()), 200);
        on(EventTypes.MODULE_TOGGLE, this::toggled);
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.2fx", (double) speed.get());
    }

    private boolean active(ClientGameContext c) {
        if (requireMouse.get() && !Controls.attackPressed(c)) {
            return false;
        }
        if (requireFalling.get() && (c.player.onGround || c.player.velocityY >= 0.0)) {
            return false;
        }
        return !requireDamage.get() || (c.focused == 1 && c.player.damagedTimer != 0);
    }

    private void update(ClientGameContext c) {
        c.client.timer.tpsScale = active(c) ? speed.get() : 1.0f;
    }

    private void toggled(ToggleEvent event) {
        if (event.module == this && (event.state & 0xff) == 0) {
            ((ClientGameContext) event.context).client.timer.tpsScale = 1.0f;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(speed);
        w.heading("Conditions");
        w.show(requireMouse);
        w.show(requireDamage);
        w.show(requireFalling);
    }
}
