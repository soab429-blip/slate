package yt.ren.slate.feature.movement;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Vec3;

public class KeepSprint extends Module {
    private final ScalarSetting chance = register("chance", new ScalarSetting("Chance", "%.0f%%", 0.01f, 100.0f, 0.0f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting motionMult = register("motion_mult", new ScalarSetting("Retained speed", "%.0f%%", 0.01f, 100.0f, 0.1f, 1.0f, 1.0f, 0.0f));
    private final BooleanSetting allowReduce = register("allow_reduce", new BooleanSetting("Allow reducing", 1, 0));
    private final BooleanSetting requireOnGround = register("require_on_ground", new BooleanSetting("On ground", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private Vec3 keepSavedVelocity = new Vec3(0.0, 0.0, 0.0);
    private int keepValid;
    private int keepRestoreSprint;

    public KeepSprint(ClientRuntime runtime) {
        super(ModuleId.KEEP_SPRINT, "Keep Sprint", "Keeps your sprint speed when attacking players.", Category.MOVEMENT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.CLICK_PRE, this::beforeKeepSprintAttack);
        on(EventTypes.CLICK_POST, this::afterKeepSprintAttack);
        on(EventTypes.UPDATE_ROTATION, this::finishKeepSprint);
    }

    @Override
    public String suffix() {
        return keepSprintLabel();
    }

    void beforeKeepSprintAttack(ClickEvent event) {
        if ((event.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = event.game();
        if (c.focused != 1 || !c.player.isSprinting() || !random.below(chance.get())) {
            keepValid = 0;
            return;
        }
        keepSavedVelocity = Motion.velocity(c.player);
        keepValid = 1;
    }

    void afterKeepSprintAttack(ClickEvent event) {
        if (keepValid != 1 || (event.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = event.game();
        LocalClientPlayerEntity player = c.player;
        if (player == null) {
            return;
        }
        if (requireOnGround.get()) {
            if (!player.onGround) {
                return;
            }
            player = c.player;
        }
        int tick = player.ticks;
        if (tick != c.player.lastAttackTime) {
            return;
        }
        if (allowReduce.get() && c.targetCache.slowdownMovesCloser != 0) {
            return;
        }
        double multiplier = motionMult.get();
        keepSavedVelocity = new Vec3(keepSavedVelocity.x * multiplier, keepSavedVelocity.y,
                multiplier * keepSavedVelocity.z);
        Motion.writeVelocity(c.player, keepSavedVelocity);
        keepRestoreSprint = 1;
    }

    void finishKeepSprint(GameEvent event) {
        if (keepRestoreSprint != 1) {
            return;
        }
        keepRestoreSprint = 0;
        ClientGameContext c = event.game();
        if (c.player != null) {
            c.player.setSprinting(true);
        }
    }

    String keepSprintLabel() {
        float percent = motionMult.get() * 100.0f;
        return ValueFormatter.format("%.0f%%", (double) percent);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(chance);
        w.show(motionMult);
        w.show(allowReduce);
        w.heading("Conditions");
        w.show(requireOnGround);
    }
}
