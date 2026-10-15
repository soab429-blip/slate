package yt.ren.slate.feature.movement;

import net.minecraft.item.Item;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.MoveEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class NoSlow extends Module {
    private final ScalarSetting multSword = register("mult_sword", new ScalarSetting("Sword", "%.0f%%", 0.0f, 100.0f, 0.2f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting multBow = register("mult_bow", new ScalarSetting("Bow", "%.0f%%", 0.0f, 100.0f, 0.2f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting multConsumables = register("mult_consumables", new ScalarSetting("Consumables", "%.0f%%", 0.0f, 100.0f, 0.2f, 1.0f, 1.0f, 0.0f));

    public NoSlow(ClientRuntime runtime) {
        super(ModuleId.NO_SLOW, "No Slow", "Removes slowdown while using items.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.MOVE_PRE, this::updateNoSlow, 200);
    }

    void updateNoSlow(MoveEvent event) {
        ClientGameContext c = event.game();
        if (!c.player.hasItemInUse()) {
            return;
        }
        Item item = Motion.heldItem(c.player);
        if (item == null) {
            return;
        }
        int id = Item.getId(item);
        int index = id - 0x105;
        ScalarSetting setting = multConsumables;
        if (Integer.compareUnsigned(index, 22) <= 0) {
            if (((0x4088c0 >>> index) & 1) != 0) {
                setting = multSword;
            } else if (index == 0) {
                setting = multBow;
            }
        }
        double multiplier = (double) setting.get() / 0.2;

        event.strafe = (float) (multiplier * (double) event.strafe);
        event.forward = (float) (multiplier * (double) event.forward);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Retained speed");
        w.show(multSword);
        w.show(multBow);
        w.show(multConsumables);
    }
}
