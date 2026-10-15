package yt.ren.slate.feature.movement;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.GameEvent;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Rotation;

public class InstantStop extends Module {

    private int instantStopPhase;
    public InstantStop(ClientRuntime runtime) {
        super(ModuleId.INSTANT_STOP, "Instant Stop", "Instantly stops moving when move keys are released.", Category.MOVEMENT);
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_PRE, this::updateInstantStop, 200);
    }
    void updateInstantStop(GameEvent event) {
        ClientGameContext c = event.game();
        if (!c.client.focused) {
            instantStopPhase = 0;
            return;
        }
        GameOptions options = c.client.options;
        KeyBinding forward = Motion.key(options, 2);
        KeyBinding back = Motion.key(options, 4);
        KeyBinding left = Motion.key(options, 3);
        KeyBinding right = Motion.key(options, 5);
        if (instantStopPhase == 2) {
            for (KeyBinding object : new KeyBinding[]{back, left, right}) {
                object.pressed = PhysicalKeys.pressed(object);
            }
            instantStopPhase = 0;
            return;
        }
        if (instantStopPhase != 1) {
            if (forward.pressed) {
                instantStopPhase = 1;
            }
            return;
        }
        if (forward.pressed || !c.player.onGround) {
            return;
        }
        Vec3 v = Motion.velocity(c.player);
        Rotation rotation = RotationLifecycle.read(c.player);
        float factor = c.player.speed;
        if (c.player.isSprinting()) {
            factor = (float) ((double) factor / 1.3);
        }
        double first = Motion.horizontalSquared(v.add(MathUtil.movementAcceleration(rotation, -1.0f, -1.0f, factor)));
        double original = Motion.horizontalSquared(v);
        int chosen = original > first ? -1 : 999;
        double best = Motion.minsd(first, original);
        double second = Motion.horizontalSquared(v.add(MathUtil.movementAcceleration(rotation, -1.0f, 0.0f, factor)));
        if (best > second) {
            chosen = 0;
        }
        best = Motion.minsd(second, best);
        double third = Motion.horizontalSquared(v.add(MathUtil.movementAcceleration(rotation, -1.0f, 1.0f, factor)));
        boolean chooseLeft = best > third;
        if (chooseLeft) {
            chosen = 1;
        }
        if (chosen == 999) {
            instantStopPhase = 0;
            return;
        }
        back.pressed = true;
        left.pressed = chooseLeft;
        right.pressed = chosen == -1;
        instantStopPhase = 2;
    }

}
