package yt.ren.slate.feature.combat;

import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerEntity;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;

import java.util.concurrent.atomic.AtomicInteger;

public class AutoBlock extends Module {
    private final ScalarSetting range = register("range", new ScalarSetting("Range", "%.1f blocks", 0.1f, 0.0f, 2.0f, 6.0f, 3.1f, 0.0f));
    private final ScalarSetting maxHurtTime = register("max_hurt_time", new ScalarSetting("Maximum hurt time", "%.0f ms", 0.0f, 50.0f, 1.0f, 10.0f, 6.0f, 0.0f));
    private final ScalarSetting maxHoldTime = register("max_hold_time", new ScalarSetting("Maximum hold duration", "%.0f ms", 0.0f, 50.0f, 1.0f, 10.0f, 5.0f, 0.0f));
    private final BooleanSetting fakeBlockAnimation = register("fake_block_animation", new BooleanSetting("Enabled", 0, 0));
    private final BooleanSetting fakeBlockAnimationRequireTarget = register("fake_block_animation_require_target", new BooleanSetting("Only within range", 0, 0));
    private final ScalarSetting lagChance = register("lag_chance", new ScalarSetting("Chance", "%.0f %%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting lagDuration = register("lag_duration", new ScalarSetting("Maximum duration", "%.0f ms", 0.0f, 50.0f, 1.0f, 10.0f, 2.0f, 0.0f));
    private final BooleanSetting lagFlushAttack = register("lag_flush_attack", new BooleanSetting("Prevent delaying attacks", 1, 0));
    private final BooleanSetting lagReblock = register("lag_reblock", new BooleanSetting("Block again immediately", 0, 0));
    private final BooleanSetting requireLeftMousePressed = register("require_left_mouse_pressed", new BooleanSetting("Left mouse pressed", 1, 0));
    private final BooleanSetting requireRightMousePressed = register("require_right_mouse_pressed", new BooleanSetting("Right mouse pressed", 0, 0));
    private final BooleanSetting requireDamage = register("require_damage", new BooleanSetting("Damaged", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private TimerRange timerRange;
    private int cooldown;
    private int heldTicks;
    private int attackTicks;
    private int targetId;
    private int fakeAnimation;
    private int wroteAnimation;
    private int suppressUse;
    private final AtomicInteger lag = new AtomicInteger();
    private int blocking;
    private int overrideKey;
    private int animationRemaining;

    public AutoBlock(ClientRuntime runtime) {
        super(ModuleId.AUTO_BLOCK, "Auto Block", "Blocks your sword for you in combat.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        timerRange = (TimerRange) runtime.module(ModuleId.TIMER_RANGE);
        on(EventTypes.TICK, e -> updateAutoBlock(e.game()), -1000);
        on(EventTypes.PACKET_SEND, this::filterAutoBlockPacket);
        on(EventTypes.MODULE_TOGGLE, this::handleAutoBlockToggle);
        on(EventTypes.CLICK_PRE, this::beforeAutoBlockUse);
        on(EventTypes.UPDATE_POST, e -> applyAutoBlockAnimation(e.game()));
    }

    private static KeyBinding useKey(ClientGameContext c) {
        return c.client.options.keyBindings[1];
    }

    private static void restoreUse(ClientGameContext c) {
        KeyBinding key = useKey(c);
        key.pressed = PhysicalKeys.pressed(key);
    }

    private static void incrementPending(KeyBinding key) {
        key.clickCount = key.clickCount + 1;
    }

    private int randomizedInt32(float value) {
        int integral = Sse.cvttss2si(value);
        float converted = (float) integral;
        float fraction = value - converted;
        int extra = random.below(fraction) ? 1 : 0;
        return integral + extra;
    }

    private boolean targetInBlockRange(ClientGameContext c) {
        if (c.targetCache.target == null) {
            return false;
        }
        float reach = range.get();
        float square = reach * reach;
        return (double) square > c.targetCache.squaredDistance;
    }

    void updateAutoBlock(ClientGameContext c) {
        fakeAnimation = 0;
        if (wroteAnimation == 1) {
            if (c.player != null && !c.player.hasItemInUse()) {
                c.player.itemUseTimer = 0;
            }
            wroteAnimation = 0;
        }
        if (cooldown != 0) {
            --cooldown;
        }
        if (c.focused == 0) {
            if (overrideKey == 1) {
                restoreUse(c);
                overrideKey = 0;
            }
            suppressUse = 0;
            lag.set(0);
            return;
        }
        int hurt = c.player.damagedTimer;
        boolean sword = TimerRange.holdingSword(c.player);
        suppressUse = 0;
        boolean candidate = false;
        boolean allowed = false;
        boolean restore = false;
        if (!sword) {
            restore = overrideKey == 1;
        } else {
            if ((requireLeftMousePressed.get() && c.keys[1] == 0)
                    || timerRange.blocksUse()) {
                restore = true;
            } else {
                suppressUse = (requireRightMousePressed.get() ? 1 : 0);
                if (suppressUse != (c.keys[2] & 0xFF)) {
                    restore = true;
                } else {
                    candidate = true;
                    if (hurt == 0 && requireDamage.get()) {
                        restore = true;
                    } else if (hurt > randomizedInt32(maxHurtTime.get())) {
                        restore = true;
                    } else {
                        int held = blocking;
                        KeyBinding key = useKey(c);
                        key.pressed = held != 0;
                        allowed = true;
                        if (held == 1) {
                            incrementPending(key);
                        }
                    }
                }
            }
        }
        if (restore) {
            KeyBinding key = useKey(c);
            key.pressed = suppressUse == 0 && PhysicalKeys.pressed(key);
        }
        if (sword || restore) {
            overrideKey = allowed ? 1 : 0;
        }
        int fake = 0;
        if (candidate && fakeBlockAnimation.get()) {
            fake = !fakeBlockAnimationRequireTarget.get() || targetInBlockRange(c) ? 1 : 0;
        }
        fakeAnimation = fake;
        if (hurt == 10) {
            cooldown = 0;
        }
        ++attackTicks;
        boolean attacked = false;
        if (Integer.compareUnsigned(attackTicks, 10) >= 0) {
            int tick = c.player.ticks;
            int damaged = c.player.lastAttackTime + 1;
            if (tick == damaged) {
                attackTicks = 0;
                attacked = true;
            }
        }
        PlayerEntity target = c.targetCache.target;
        int id = target != null ? target.networkId : 0;
        if (id != targetId) {
            targetId = id;
            attackTicks = 0;
            attacked = true;
        }
        if (lag.get() != 0) {
            if (((attacked ? 1 : 0) & (lagFlushAttack.get() ? 1 : 0)) == 1) {
                lag.getAndSet(0);
            } else {
                lag.getAndDecrement();
            }
            if (lagReblock.get() && lag.get() == 0) {
                cooldown = 0;
            }
        }
        if (blocking != 0) {
            int held = ++heldTicks;
            int limit = randomizedInt32(maxHoldTime.get());
            if (allowed && held < limit) {
                return;
            }
            useKey(c).pressed = false;
            overrideKey = 1;
            if (random.below(lagChance.get())) {
                lag.getAndSet(randomizedInt32(lagDuration.get()));
            }
            blocking = 0;
            return;
        }
        if (!allowed || cooldown != 0 || !targetInBlockRange(c)) {
            return;
        }
        KeyBinding key = useKey(c);
        key.pressed = true;
        incrementPending(key);
        overrideKey = 1;
        blocking = 1;
        cooldown = 10;
        heldTicks = 0;
        lag.getAndSet(0);
    }

    void filterAutoBlockPacket(PacketEvent e) {
        if (lag.get() != 0) {
            e.action = 1;
        }
    }

    void handleAutoBlockToggle(ToggleEvent e) {
        if (e.module != this || (e.state & 0xff) != 0 || (blocking & 1) == 0) {
            return;
        }
        ClientGameContext c = (ClientGameContext) e.context;
        if (overrideKey == 1) {
            restoreUse(c);
            overrideKey = 0;
        }
        blocking = 0;
        lag.getAndSet(0);
    }

    void beforeAutoBlockUse(ClickEvent e) {
        if ((e.left & 0xff) != 0) {
            return;
        }
        ClientGameContext c = e.game();
        if (!TimerRange.holdingSword(c.player)) {
            lag.getAndSet(0);
        }
        if (suppressUse == 1 && (blocking & 1) == 0) {
            e.cancelled = 1;
        }
    }

    void applyAutoBlockAnimation(ClientGameContext c) {
        if (fakeAnimation == 0) {
            animationRemaining = 72000;
            return;
        }
        if (c.player == null) {
            return;
        }
        wroteAnimation = 1;
        --animationRemaining;
        c.player.itemUseTimer = animationRemaining;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(range);
        w.show(maxHurtTime);
        w.show(maxHoldTime);
        w.heading("Force block animation");
        w.show(fakeBlockAnimation);
        if (fakeBlockAnimation.get())
            w.show(fakeBlockAnimationRequireTarget);
        w.heading("Lag");
        w.show(lagChance);
        w.show(lagDuration);
        w.show(lagFlushAttack);
        w.show(lagReblock);
        w.heading("Conditions");
        w.show(requireLeftMousePressed);
        w.show(requireRightMousePressed);
        w.show(requireDamage);
    }
}
