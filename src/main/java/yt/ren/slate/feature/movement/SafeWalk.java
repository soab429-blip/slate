package yt.ren.slate.feature.movement;

import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.feature.utility.ItemUse;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.hook.ClientHooks;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class SafeWalk extends Module {
    private final ScalarSetting motionMultiplier = register("motion_multiplier", new ScalarSetting("Motion", "%.2fx", 0.0f, 0.0f, 0.8f, 1.0f, 1.0f, 0.0f));
    private final BooleanSetting requireHoldingBlocks = register("require_holding_blocks", new BooleanSetting("Holding blocks", 0, 0));
    private final BooleanSetting requireLookingDown = register("require_looking_down", new BooleanSetting("Looking down", 0, 0));
    private final BooleanSetting requireNotForward = register("require_not_forward", new BooleanSetting("Not moving forward", 0, 0));

    public SafeWalk(ClientRuntime runtime) {
        super(ModuleId.SAFE_WALK, "Safe Walk", "Prevents you from walking off ledges.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()));
    }

    private boolean conditionsMet(ClientGameContext c) {
        if (requireLookingDown.get() && 65.0f > RotationLifecycle.read(c.player).pitch) {
            return false;
        }
        if (requireNotForward.get() && c.player.forwardSpeed > 0.0f) {
            return false;
        }
        return !requireHoldingBlocks.get() || ItemUse.itemIsNonAirBlock(HeldItem.selected(c.player.inventory));
    }

    private void update(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        if (!conditionsMet(c)) {
            ClientHooks.safeWalkClip = 0;
            return;
        }
        ClientHooks.safeWalkClip = 1;
        float multiplier = motionMultiplier.get();
        if (c.player.onGround && multiplier != 1.0f) {
            c.player.velocityX = c.player.velocityX * multiplier;
            c.player.velocityZ = multiplier * c.player.velocityZ;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(motionMultiplier);
        w.heading("Conditions");
        w.show(requireHoldingBlocks);
        w.show(requireLookingDown);
        w.show(requireNotForward);
    }
}
