package yt.ren.slate.feature.movement;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.event.Events.MoveEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class Sprint extends Module {
    private final BooleanSetting whileUsingItem = register("while_using_item", new BooleanSetting("Using item", 0, 0));
    private final BooleanSetting whileBackwards = register("while_backwards", new BooleanSetting("Backwards", 0, 0));
    private final BooleanSetting whileSideways = register("while_sideways", new BooleanSetting("Sideways", 0, 0));
    private final BooleanSetting whileInGui = register("while_in_gui", new BooleanSetting("In inventory", 0, 0));

    private int sprintPreviouslyAllowed;

    public Sprint(ClientRuntime runtime) {
        super(ModuleId.SPRINT, "Sprint", "Automatically sprints for you.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_ROTATION, this::updateSprint, 100);
        on(EventTypes.MOVE_PRE, this::updateSprintMovement, 100);
    }

    void updateSprint(GameEvent event) {
        ClientGameContext c = event.game();
        InventoryMenuScreen current = Motion.screen(c.client);
        int allowed = ((current == null ? 1 : 0) | (whileInGui.get() ? 1 : 0)) & 1;
        GameOptions options = c.client.options;
        KeyBinding binding = Motion.key(options, 8);
        binding.pressed = allowed != 0;
        LocalClientPlayerEntity player = c.player;
        if (player != null) {
            if (allowed == 0) {
                if (sprintPreviouslyAllowed == 1) {
                    player.setSprinting(false);
                }
                return;
            }
            if (player.isSprinting()) {
                c.player.setSprinting(true);
            }
        }
        sprintPreviouslyAllowed = allowed;
    }

    void updateSprintMovement(MoveEvent event) {
        ClientGameContext c = event.game();
        if (Motion.stationary(event)) {
            return;
        }
        boolean usingNow = c.player.hasItemInUse();
        if (usingNow && !whileUsingItem.get()) {
            return;
        }
        if (event.forward < 0.0f) {
            if (!whileBackwards.get()) {
                return;
            }
        } else if (event.forward == 0.0f && event.strafe != 0.0f) {
            if (!whileSideways.get()) {
                return;
            }
        } else if (!usingNow) {
            return;
        }
        event.friction = (float) ((double) event.friction * 1.3);
        c.player.setSprinting(true);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Allow while");
        w.show(whileUsingItem);
        w.show(whileBackwards);
        w.show(whileSideways);
        w.show(whileInGui);
    }
}
