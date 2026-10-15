package yt.ren.slate.core;

import net.minecraft.client.TickTimer;
import yt.ren.slate.aim.AimState;
import yt.ren.slate.aim.rotation.RotationApply;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.util.Clock;
import yt.ren.slate.util.structs.Rotation;

public class ClientFrame {
    public static class State {
        public double lastUpdate;
        public Rotation previousRotation = new Rotation(0.0f, 0.0f);
    }

    public static void update(ClientGameContext context, ModuleRegistry registry, TickTimer timer,
                              double now, State state, double[] sharedClock, Clock clock,
                              AimState aim, RotationLifecycle rotation) {
        if (state.lastUpdate == 0.0) {
            state.lastUpdate = now;
        }
        sharedClock[0] = clock.now();
        if (context.world == null || context.player == null) {
            state.lastUpdate = now;
            return;
        }
        Rotation current = RotationLifecycle.read(context.player);
        Rotation delta = new Rotation(current.yaw - state.previousRotation.yaw,
                current.pitch - state.previousRotation.pitch);
        if ((Float.floatToRawIntBits(delta.yaw) & 0x7fffffff) != 0
                || (Float.floatToRawIntBits(delta.pitch) & 0x7fffffff) != 0) {
            RotationApply.accumulateMouseRotation(aim, delta);
        }
        double elapsed = now - state.lastUpdate;
        if (0.002 > elapsed) {
            return;
        }
        float partial = timer.partialTick;
        registry.post(EventTypes.FRAME, new FrameEvent(context, partial, elapsed));
        RotationLifecycle.applyRotation(context.player, rotation.shared.accumulator, rotation.step, 0);
        state.lastUpdate = now;
        state.previousRotation = RotationLifecycle.read(context.player);
    }
}
