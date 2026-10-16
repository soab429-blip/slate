package yt.ren.slate.feature.utility;

import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.ModuleRegistry.VetoEvent;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;

public class AutoPot extends AutomaticItemModule {
    private final ScalarSetting delay = register("delay", new ScalarSetting("Delay between actions", "%.0f ms", 0.1f, 50.0f, 0.0f, 2.0f, 0.5f, 0.0f));
    private final BooleanSetting preventWaste = register("prevent_waste", new BooleanSetting("Prevent wasting health", 1, 0));
    private final BooleanSetting doubleSplash = register("double_splash", new BooleanSetting("Automatically do multiple splashes", 0, 0));
    private final BooleanSetting useNonHealth = register("use_non_health", new BooleanSetting("Use non-health potions", 0, 0));

    public AutoPot(ClientRuntime runtime) {
        super(ModuleId.AUTO_POT, "Auto Pot", "Uses healing potions for you.", Category.UTILITY, runtime);
        traits(Trait.NO_TOGGLE, Trait.HIDDEN, Trait.NO_HOLD);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updatePot(e.game()));
        on(EventTypes.MODULE_VETO, this::deferPotDisable);
        on(EventTypes.MODULE_TOGGLE, this::potToggle);
    }

    private static class PotState {
        int phase;
        int selectedSlot;
        int restoreSlot;
        int repeat;
        float remaining;
        int uses;
        final TimedNoise delayNoise = new TimedNoise();
        final TimedNoise extraProbability = new TimedNoise();
        int extraUses;
    }

    private final PotState pot = new PotState();

    float autoPotDelay() {
        return shortRandomDelay(delay.get(), pot.delayNoise, pot.extraProbability);
    }

    void updatePot(ClientGameContext c) {
        PotState state = pot;
        for (int transition = 0; transition < 4; ++transition) {
            if (state.remaining > 0.0f) {
                state.remaining -= 1.0f;
                finishPot(c);
                return;
            }
            if (c.focused == 0) {
                state.repeat = 0;
                state.phase = 4;
                finishPot(c);
                return;
            }
            switch (state.phase) {
                case 0: {
                    PlayerInventory items = c.player.inventory;
                    int chosen = -1;
                    for (int index = 0; index < 9; ++index) {
                        ItemStack stack = items.getItem(index);
                        if (stack == null) {
                            continue;
                        }
                        Item item = stack.getItem();
                        if (item == null || Item.getId(item) != 0x175) {
                            continue;
                        }
                        int damage = stack.metadata;
                        if ((damage & 0x4000) == 0 || ((damage & 15) != 5 && !useNonHealth.get())) {
                            continue;
                        }
                        chosen = index;
                        break;
                    }
                    if (chosen == -1) {
                        state.uses = 0;
                        state.phase = 4 - state.repeat;
                        state.repeat = 0;
                        finishPot(c);
                        return;
                    }
                    state.selectedSlot = chosen;
                    int saved;
                    if (state.repeat == 1) {
                        saved = state.restoreSlot;
                    } else {
                        saved = items.selectedSlot;
                        state.restoreSlot = saved;
                        chosen = state.selectedSlot;
                    }
                    state.repeat = 0;
                    state.phase = 1 + (chosen == saved ? 1 : 0);
                    break;
                }
                case 1: {
                    if (!ModuleRegistry.claimOwner(runtime.owner, this)) {
                        state.phase = 4;
                        finishPot(c);
                        return;
                    }
                    c.player.inventory.selectedSlot = state.selectedSlot;
                    state.phase = 2;
                    state.remaining = autoPotDelay() + state.remaining;
                    break;
                }
                case 2:
                    if (c.player.hasItemInUse()) {
                        finishPot(c);
                        return;
                    }
                    actionPress(c, false, true);
                    state.uses = state.uses - 1;
                    state.phase = 3;
                    if (state.selectedSlot != state.restoreSlot) {
                        state.remaining = (autoPotDelay() + 1.0f) + state.remaining;
                    }
                    break;
                case 3:
                    if (state.uses > 0) {
                        state.repeat = 1;
                        state.phase = 0;
                        break;
                    }
                    c.player.inventory.selectedSlot = state.restoreSlot;
                    state.phase = 4;
                    break;
                case 4:
                    ModuleRegistry.releaseOwner(runtime.owner, this);
                    finishPot(c);
                    return;
                default:
                    return;
            }
        }
        finishPot(c);
    }

    private void finishPot(ClientGameContext c) {
        if (pot.phase == 4) {
            requestDisable(c);
        }
    }

    void deferPotDisable(VetoEvent event) {
        if (pot.extraUses > 0 && pot.phase != 4 && event.module() == this) {
            event.veto = 1;
            pot.uses = pot.uses + 1;
            pot.extraUses = pot.extraUses - 1;
        }
    }

    void potToggle(ToggleEvent event) {
        if (event.module != this) {
            return;
        }
        if (event.state != 1) {
            pot.phase = 0;
            pot.uses = 0;
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        if (c.focused != 1) {
            return;
        }
        float current = AttackPrediction.health(c.player);
        float maximum = c.player.getMaxHealth();
        if ((Float.floatToRawIntBits(maximum) & 0x7fffffff) > 0x7f800000) {
            maximum = 20.0f;
        }
        double missing = maximum - current;
        int uses = Sse.cvttsd2si((missing + 0.5) * 0.125);
        if (uses == 0) {
            uses = 1;
            if (preventWaste.get()) {
                pot.phase = 4;
                return;
            }
        }
        if (doubleSplash.get()) {
            pot.uses = uses;
            pot.extraUses = 0;
        } else {
            pot.uses = 1;
            pot.extraUses = preventWaste.get() ? uses - 1 : 5;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(delay);
        w.show(preventWaste);
        w.show(doubleSplash);
        w.show(useNonHealth);
    }
}
