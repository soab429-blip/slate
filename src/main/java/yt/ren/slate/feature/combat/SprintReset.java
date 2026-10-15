package yt.ren.slate.feature.combat;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class SprintReset extends Module {
    private static final int SNEAK = 1;
    private static final int NO_STOP = 2;

    private enum Phase { IDLE, WAITING, STOPPED }

    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 3, 0, new String[]{"WTap", "Sneak", "NoStop"}, 0, 0, 0));
    private final ScalarSetting delay = register("delay", new ScalarSetting("Delay", "%.0f ms", 0.0f, 50.0f, 0.0f, 8.0f, 4.0f, 0.0f));
    private final ScalarSetting duration = register("duration", new ScalarSetting("Stop duration", "%.0f ms", 0.0f, 50.0f, 1.0f, 6.0f, 2.0f, 0.0f));
    private final BooleanSetting randomize = register("randomize", new BooleanSetting("Randomize", 1, 0));
    private final BooleanSetting waitForDamage = register("wait_for_damage", new BooleanSetting("Damaged", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private final TimedNoise delayNoise = new TimedNoise();
    private final TimedNoise durationNoise = new TimedNoise();
    private final TimedNoise extraProbability = new TimedNoise();
    private Phase phase = Phase.IDLE;
    private float remaining;
    private double lastTrigger;
    private boolean pending;
    private int ticksSinceDamage;

    public SprintReset(ClientRuntime runtime) {
        super(ModuleId.SPRINT_RESET, "Sprint Reset", "Resets your sprint state to deal more knockback.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.UPDATE_ROTATION, e -> update(e.game()), -100);
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    private void setStopped(ClientGameContext c, boolean stopped) {
        GameOptions options = c.client.options;
        int selected = mode.index();
        KeyBinding binding = selected == NO_STOP ? options.keyBindings[8] : options.keyBindings[selected == SNEAK ? 7 : 2];
        if (selected == NO_STOP) {
            c.player.setSprinting(!stopped);
        }
        binding.pressed = stopped ? selected == SNEAK : PhysicalKeys.pressed(binding);
    }

    private void reset(ClientGameContext c) {
        if (phase == Phase.STOPPED) {
            setStopped(c, false);
        }
        phase = Phase.IDLE;
        pending = false;
        remaining = 0.0f;
    }

    private void trackAttack(ClientGameContext c) {
        double now = runtime.clock.now();
        if (now - lastTrigger >= 0.5 && c.player.ticks == c.player.lastAttackTime) {
            lastTrigger = now;
            pending = true;
        }
        ++ticksSinceDamage;
        if (c.player.damagedTimer == 10) {
            ticksSinceDamage = 0;
        }
    }

    private float stopDuration() {
        float stop = duration.get();
        if (randomize.get()) {
            double now = runtime.frameTime[0];
            stop = Math.max(0.0f, stop - durationNoise.sample(random, now, 0.0f, 1.0f, 30.0));
            if (extraProbability.belowSample(random, now, 0.05f, 0.1f, 30.0)) {
                stop += random.range(1.0f, 2.0f);
            }
        }
        return stop;
    }

    private void update(ClientGameContext c) {
        if (c.focused != 1) {
            reset(c);
            return;
        }
        trackAttack(c);
        if (remaining > 0.0f) {
            remaining -= 1.0f;
            if (remaining > 0.0f) {
                if (phase == Phase.STOPPED) {
                    setStopped(c, true);
                }
                return;
            }
        }
        if (phase == Phase.STOPPED) {
            setStopped(c, false);
            phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.WAITING) {
            setStopped(c, true);
            phase = Phase.STOPPED;
            pending = false;
            remaining += stopDuration();
            return;
        }
        float wait = delay.get();
        if (randomize.get()) {
            wait += delayNoise.sample(random, runtime.frameTime[0], 0.0f, 2.0f, 5.0);
        }
        if (!pending || (waitForDamage.get() && ticksSinceDamage != 0)) {
            return;
        }
        if (requireWeapon.get() && !HeldItem.holdingWeapon(runtime, c.player)) {
            return;
        }
        remaining += wait;
        phase = Phase.WAITING;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
        w.show(delay);
        w.show(duration);
        w.show(randomize);
        w.heading("Conditions");
        w.show(waitForDamage);
        w.show(requireWeapon);
    }
}
