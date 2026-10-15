package yt.ren.slate.feature.player;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.feature.combat.Weapons;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.ModuleRegistry.VetoEvent;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.ScalarRounding;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;

public class AutoRod extends AutomaticItemModule {
    private final ScalarSetting retractSpeed = register("retract_speed", new ScalarSetting("Retract speed", "%.2fx", 0.01f, 0.0f, 0.5f, 2.0f, 1.0f, 0.0f));
    private final ScalarSetting delay = register("delay", new ScalarSetting("Delay between actions", "%.0f ms", 0.1f, 50.0f, 0.0f, 2.0f, 0.5f, 0.0f));
    private final BooleanSetting randomizeDelays = register("randomize_delays", new BooleanSetting("Randomize delays", 1, 0));
    private final BooleanSetting alwaysSwitchToWeapon = register("always_switch_to_weapon", new BooleanSetting("Always switch back to weapon", 0, 0));
    private final BooleanSetting allowThrowables = register("allow_throwables", new BooleanSetting("Allow using snowballs/eggs", 0, 0));
    private final ScalarSetting throwSpeed = register("throw_speed", new ScalarSetting("Throw speed", "%.1f CPS", 0.1f, 0.0f, 4.0f, 20.0f, 5.0f, 0.0f));
    private final BooleanSetting requireNotMouseOver = register("require_not_mouse_over", new BooleanSetting("Not within melee range", 0, 0));

    public AutoRod(ClientRuntime runtime) {
        super(ModuleId.AUTO_ROD, "Auto Rod", "Throws your fishing rod for you.", Category.UTILITY, runtime);
        traits(Trait.NO_TOGGLE, Trait.HIDDEN);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updateRod(e.game()));
        on(EventTypes.MODULE_VETO, this::deferRodDisable);
        on(EventTypes.MODULE_TOGGLE, this::rodToggle);
        on(EventTypes.CLICK_PRE, this::beforeRodUse);
    }

    private static class RodState {
        int stopRequested;
        int phase;
        int selectedSlot;
        float remaining;
        int retractTicks;
        int isRod;
        int alreadyUsed;
        final TimedNoise delayNoise = new TimedNoise();
        final TimedNoise extraProbability = new TimedNoise();
        final TimedNoise throwNoise = new TimedNoise();
        final TimedNoise throwProbability = new TimedNoise();
        int restoreSlot = -1;
    }

    private final RodState rod = new RodState();

    float autoRodDelay() {
        float base = delay.get();
        if (!randomizeDelays.get()) {
            return base;
        }
        return shortRandomDelay(base, rod.delayNoise, rod.extraProbability);
    }

    float autoRodThrowDelay() {
        float cps = throwSpeed.get();
        if (!randomizeDelays.get()) {
            return 20.0f / cps;
        }
        cps = cps * noise(rod.throwNoise, 0.5f, 1.0f, 1.0);
        float result = 20.0f / cps;
        if (chance(rod.throwProbability, Float.intBitsToFloat(0x3c23d70a), Float.intBitsToFloat(0x3cf5c28f), 30.0)) {
            result = result + random.range(1.0f, 2.0f);
        }
        return result;
    }

    int autoRodRetractTicks(ClientGameContext c) {
        PlayerEntity target = c.targetCache.target;
        float ticks;
        if (target == null) {
            ticks = 10.0f / retractSpeed.get();
        } else {
            Vec3 origin = new Vec3(c.player.x, c.player.y, c.player.z).addStandingEyeHeight();
            RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) target;
            Vec3 position = new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ);
            Bounds box = new Bounds(position.subtract(new Vec3(0.3, 0.0, 0.3)), position.add(new Vec3(0.3, 1.8, 0.3)));
            double length = Geometry.distance(origin, Geometry.nearestPoint(box, origin));
            ticks = (float) (length / (double) retractSpeed.get());
        }
        return Sse.cvttss2si(ScalarRounding.ceilGameFloat(ticks));
    }

    private int findRodOrThrowable(PlayerInventory items) {
        rod.isRod = 0;
        int candidate = -1;
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = items.getItem(slot);
            if (stack == null) {
                continue;
            }
            Item item = stack.getItem();
            if (item == null) {
                continue;
            }
            int id = Item.getId(item);
            if (id == 0x14c || id == 0x158) {
                if (allowThrowables.get()) {
                    rod.isRod = 0;
                    return slot;
                }
            } else if (id == 0x15a) {
                rod.isRod = 1;
                candidate = slot;
                if (!allowThrowables.get()) {
                    return slot;
                }
            }
        }
        return candidate;
    }

    private void restoreRodSlot(ClientGameContext c) {
        if (rod.restoreSlot != -1) {
            c.player.inventory.selectedSlot = rod.restoreSlot;
            rod.restoreSlot = -1;
        }
    }

    private void finishRod(ClientGameContext c) {
        if (rod.phase == 6) {
            if (holdToActivate()) {
                rod.phase = 0;
            } else {
                requestDisable(c);
            }
        }
    }

    void updateRod(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        RodState state = rod;
        if (state.stopRequested != 0) {
            restoreRodSlot(c);
            ModuleRegistry.releaseOwner(runtime.owner, this);
            requestDisable(c);
            state.stopRequested = 0;
            return;
        }
        for (int transition = 0; transition < 3; ++transition) {
            if (requireNotMouseOver.get()) {
                HitResult hit = c.client.crosshairTarget;
                if (hit != null && hit.type.ordinal() == 2) {
                    restoreRodSlot(c);
                    ModuleRegistry.releaseOwner(runtime.owner, this);
                    state.phase = 6;
                    finishRod(c);
                    return;
                }
            }
            if (state.phase == 7) {
                restoreRodSlot(c);
                ModuleRegistry.releaseOwner(runtime.owner, this);
                state.phase = 0;
                return;
            }
            if (state.retractTicks != 0) {
                state.retractTicks = state.retractTicks - 1;
            }
            if (state.remaining > 0.0f) {
                state.remaining = state.remaining + -1.0f;
                if (state.remaining > 0.0f) {
                    break;
                }
            }
            PlayerInventory items = c.player.inventory;
            int current = items.selectedSlot;
            int selected = state.selectedSlot;
            if (Integer.compareUnsigned(state.phase, 2) >= 0 && current != selected) {
                ModuleRegistry.releaseOwner(runtime.owner, this);
                state.phase = 6;
                finishRod(c);
                return;
            }
            switch (state.phase) {
                case 0: {
                    int candidate = findRodOrThrowable(items);
                    if (candidate == -1 || !ModuleRegistry.claimOwner(runtime.owner, this)) {
                        state.phase = 6;
                        finishRod(c);
                        return;
                    }
                    state.selectedSlot = candidate;
                    state.restoreSlot = current;
                    int restore = current;
                    if (alwaysSwitchToWeapon.get()) {
                        int weapon = runtime.module(Weapons.class).findSlot(items);
                        if (weapon != -1) {
                            restore = weapon;
                            state.restoreSlot = weapon;
                        } else {
                            restore = state.restoreSlot;
                        }
                    }
                    state.phase = 1 + (state.selectedSlot == restore ? 1 : 0);
                    break;
                }
                case 1:
                    items.selectedSlot = selected;
                    state.phase = 2;
                    state.alreadyUsed = 0;
                    state.remaining = autoRodDelay() + state.remaining;
                    break;
                case 2: {
                    int candidate = findRodOrThrowable(items);
                    if (candidate == -1) {
                        state.phase = 5;
                        return;
                    }
                    if (candidate != current) {
                        state.selectedSlot = candidate;
                        state.phase = 1;
                        state.remaining = autoRodDelay() + state.remaining;
                        break;
                    }
                    actionPress(c, false, false);
                    float delay;
                    if (state.isRod == 1) {
                        state.phase = 3;
                        state.retractTicks = autoRodRetractTicks(c);
                        delay = autoRodDelay();
                    } else if (holdToActivate()) {
                        delay = autoRodThrowDelay();
                    } else {
                        state.phase = 5;
                        delay = autoRodDelay();
                    }
                    state.remaining = delay + state.remaining;
                    finishRod(c);
                    return;
                }
                case 3:
                    if (state.retractTicks != 0) {
                        finishRod(c);
                        return;
                    }
                    state.phase = (state.restoreSlot == selected ? 1 : 0) ^ 5;
                    state.remaining = autoRodDelay() + state.remaining;
                    break;
                case 4:
                    items.selectedSlot = (state.restoreSlot + 1) % 9;
                    state.phase = 5;
                    return;
                case 5:
                    state.phase = 6;
                    restoreRodSlot(c);
                    ModuleRegistry.releaseOwner(runtime.owner, this);
                    state.remaining = autoRodDelay() + state.remaining;
                    finishRod(c);
                    return;
                default:
                    finishRod(c);
                    return;
            }
        }
        finishRod(c);
    }

    void deferRodDisable(VetoEvent event) {
        if (rod.stopRequested != 0 || event.module() != this) {
            return;
        }
        if (holdToActivate()) {
            rod.stopRequested = 1;
        } else {
            if (rod.phase == 6) {
                return;
            }
            if (rod.phase == 3) {
                rod.phase = 7;
            }
        }
        event.veto = 1;
    }

    void rodToggle(ToggleEvent event) {
        if (event.module == this && event.state == 1) {
            rod.phase = 0;
        }
    }

    void beforeRodUse(ClickEvent event) {
        if (event.left != 0) {
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        PlayerInventory inventory = c.player.inventory;
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return;
        }
        ItemStack stack = inventory.items[slot];
        if (stack == null) {
            return;
        }
        Item item = stack.getItem();
        if (item == null || Item.getId(item) != 0x15a) {
            return;
        }
        if (rod.alreadyUsed == 0) {
            rod.alreadyUsed = 1;
        } else {
            event.cancelled = 1;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(retractSpeed);
        w.show(delay);
        w.show(randomizeDelays);
        w.show(alwaysSwitchToWeapon);
        w.heading("Throwables");
        w.show(allowThrowables);
        w.show(throwSpeed);
        w.heading("Main");
        w.show(requireNotMouseOver);
    }
}
