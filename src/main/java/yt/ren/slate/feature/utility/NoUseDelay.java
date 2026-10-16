package yt.ren.slate.feature.utility;

import net.minecraft.client.options.KeyBinding;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class NoUseDelay extends Module {
    private final BooleanSetting allowDesync = register("allow_desync", new BooleanSetting("Allow desync abuse", 0, 0));

    private int remaining;
    private int selectedSlot;

    public NoUseDelay(ClientRuntime runtime) {
        super(ModuleId.NO_USE_DELAY, "No Use Delay", "Removes vanilla delay when consuming items.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_POST, e -> update(e.game()));
    }

    private void update(ClientGameContext c) {
        if (c.focused == 0) {
            remaining = 0;
            return;
        }
        KeyBinding use = c.client.options.keyBindings[1];
        boolean pressed = use.pressed;
        int slot = c.player.inventory.selectedSlot;
        if (slot != selectedSlot) {
            remaining = 0;
            selectedSlot = slot;
            use.pressed = PhysicalKeys.pressed(use);
        }
        if (!pressed) {
            return;
        }
        int useTimer = c.player.itemUseTimer;
        if (remaining <= 0) {
            remaining = useTimer;
            return;
        }
        --remaining;
        if (remaining == 0 || (useTimer == 0 && allowDesync.get())) {
            use.pressed = false;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(allowDesync);
    }
}
