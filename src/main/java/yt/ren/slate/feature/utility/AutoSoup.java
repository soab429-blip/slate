package yt.ren.slate.feature.utility;

import net.minecraft.entity.living.player.PlayerInventory;
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
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.setting.SettingLists.NamedItem;
import yt.ren.slate.ui.SettingsWidgets;

public class AutoSoup extends AutomaticItemModule {
    private final ScalarSetting singleSoupMaxHp = register("single_soup_max_hp", new ScalarSetting("Single use", "%.1f\u2665", 1.0f, 0.5f, 0.0f, 20.0f, 13.0f, 0.0f));
    private final ScalarSetting doubleSoupMaxHp = register("double_soup_max_hp", new ScalarSetting("Double use", "%.1f\u2665", 1.0f, 0.5f, 0.0f, 20.0f, 6.0f, 0.0f));
    private final ScalarSetting randomization = register("randomization", new ScalarSetting("Randomization", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.5f, 0.0f));
    private final ScalarSetting delayBefore = register("delay_before", new ScalarSetting("Before switching slots", "%.0f ms", 0.1f, 50.0f, 0.0f, 10.0f, 2.0f, 0.0f));
    private final ScalarSetting delayBetween = register("delay_between", new ScalarSetting("Between actions", "%.0f ms", 0.1f, 50.0f, 0.0f, 2.0f, 0.5f, 0.0f));
    private final ScalarSetting delayAfter = register("delay_after", new ScalarSetting("Cooldown", "%.0f ms", 0.0f, 50.0f, 5.0f, 20.0f, 10.0f, 0.0f));
    private final BooleanSetting dropBowls = register("drop_bowls", new BooleanSetting("Drop bowls", 0, 0));
    private final BooleanSetting overrideItem = register("override_item", new BooleanSetting("Override item type", 0, 0));
    private final ListSetting allowedItems = register("allowed_items", new ListSetting("Items", "Search item", 0));

    public AutoSoup(ClientRuntime runtime) {
        super(ModuleId.AUTO_SOUP, "Auto Soup", "Uses soups for you.", Category.UTILITY, runtime);
        allowedItems.attachStorage(runtime.lists.autoSoupStorage());
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updateSoup(e.game()), -100);
        on(EventTypes.MODULE_VETO, this::deferSoupDisable);
        on(EventTypes.MODULE_TOGGLE, this::soupToggle);
    }

    private static class SoupState {
        int stopRequested;
        int phase;
        int nextSoup;
        int selectedSlot;
        int restoreSlot;
        int doubleSoup;
        int leavesBowl;
        float remaining;
        final TimedNoise delayNoise = new TimedNoise();
        final TimedNoise extraProbability = new TimedNoise();
        final TimedNoise selectionProbability = new TimedNoise();
    }

    private final SoupState soup = new SoupState();

    private boolean containsNamedItem(int id) {
        return runtime.lists.autoSoup.find(new NamedItem(null, id), (a, b) -> a.id - b.id) != null;
    }

    float autoSoupDelay(float base) {
        float first = noise(soup.delayNoise, 0.0f, 0.5f, 60.0);
        float value = noise(soup.delayNoise, 0.0f, 0.5f, 5.0) + first;
        if (chance(soup.extraProbability, Float.intBitsToFloat(0x3cf5c28f), Float.intBitsToFloat(0x3d75c28f), 60.0)) {
            value = value + random.range(0.5f, 2.5f);
        }
        return value * randomization.get() + base;
    }

    private void finishSoupAction() {
        soup.phase = 5;
        if (soup.selectedSlot != soup.restoreSlot) {
            soup.remaining = autoSoupDelay(delayBetween.get()) + soup.remaining;
            if (soup.remaining <= 0.0f) {
                soup.remaining = soup.remaining + 1.0f;
            }
        }
    }

    void updateSoup(ClientGameContext c) {
        SoupState state = soup;
        if (state.stopRequested == 1 && (state.phase == 0 || state.phase == 6)) {
            requestDisable(c);
            return;
        }
        if (c.focused != 1) {
            state.nextSoup = 0;
            state.phase = 0;
            return;
        }
        for (int transition = 0; transition < 3; ++transition) {
            if (state.remaining > 0.0f) {
                state.remaining -= 1.0f;
                return;
            }
            switch (state.phase) {
                case 0: {
                    float current = AttackPrediction.health(c.player);
                    if (current > singleSoupMaxHp.get()) {
                        return;
                    }
                    state.remaining = autoSoupDelay(delayBefore.get()) + state.remaining;
                    state.doubleSoup = doubleSoupMaxHp.get() >= current ? 1 : 0;
                    state.phase = 1;
                    break;
                }
                case 1: {
                    PlayerInventory items = c.player.inventory;
                    int next = state.nextSoup;
                    int current = items.selectedSlot;
                    ItemStack[] contents = items.items;
                    int chosen = -1;
                    for (int offset = 0; offset < 9; ++offset) {
                        int candidate = (current + offset) % 9;
                        if ((next & (candidate == current ? 1 : 0)) != 0) {
                            continue;
                        }
                        ItemStack stack = contents[candidate];
                        int id = stack != null ? itemId(stack) : 0;
                        boolean accepted = overrideItem.get()
                                ? containsNamedItem(id) : (id == 0x11a || id == 0x19d);
                        if (!accepted) {
                            continue;
                        }
                        state.leavesBowl = (id == 0x11a || id == 0x19d) ? 1 : 0;
                        chosen = candidate;
                        if (!chance(state.selectionProbability, Float.intBitsToFloat(0x3c23d70a),
                                Float.intBitsToFloat(0x3da3d70a), 30.0)) {
                            break;
                        }
                    }
                    if (chosen == -1) {
                        state.phase = 6 - state.nextSoup;
                        state.nextSoup = 0;
                        return;
                    }
                    state.selectedSlot = chosen;
                    int saved;
                    if (state.nextSoup == 1) {
                        saved = state.restoreSlot;
                    } else {
                        saved = items.selectedSlot;
                        state.restoreSlot = saved;
                        chosen = state.selectedSlot;
                    }
                    state.nextSoup = 0;
                    state.phase = 2 | (chosen == saved ? 1 : 0);
                    break;
                }
                case 2: {
                    if (!ModuleRegistry.claimOwner(runtime.owner, this)) {
                        state.phase = 0;
                        return;
                    }
                    c.player.inventory.selectedSlot = state.selectedSlot;
                    state.phase = 3;
                    state.remaining = autoSoupDelay(delayBetween.get()) + state.remaining;
                    if (c.player.hasItemInUse() && state.remaining <= 0.0f) {
                        state.remaining = state.remaining + 1.0f;
                    }
                    break;
                }
                case 3:
                    actionPress(c, false, true);
                    if (dropBowls.get() && state.leavesBowl != 0) {
                        state.phase = 4;
                        float delay = autoSoupDelay(delayBetween.get()) + state.remaining;
                        state.remaining = delay <= 0.0f ? delay + 1.0f : delay;
                        break;
                    }
                    finishSoupAction();
                    break;
                case 4:
                    actionPress(c, true, true);
                    finishSoupAction();
                    break;
                case 5: {
                    ModuleRegistry.releaseOwner(runtime.owner, this);
                    if (state.doubleSoup == 1) {
                        state.doubleSoup = 0;
                        state.nextSoup = 1;
                        state.phase = 1;
                        break;
                    }
                    c.player.inventory.selectedSlot = state.restoreSlot;
                    state.phase = 6;
                    break;
                }
                case 6:
                    state.nextSoup = 0;
                    state.remaining = autoSoupDelay(delayAfter.get()) + state.remaining;
                    state.phase = 0;
                    break;
                default:
                    return;
            }
        }
    }

    void deferSoupDisable(VetoEvent event) {
        if (event.module() == this && soup.phase != 0 && soup.phase != 6) {
            event.veto = 1;
            soup.stopRequested = 1;
        }
    }

    void soupToggle(ToggleEvent event) {
        if (event.module == this && event.state == 1) {
            soup.phase = 0;
            soup.nextSoup = 0;
            soup.remaining = 0.0f;
            soup.stopRequested = 0;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Maximum health");
        w.show(singleSoupMaxHp);
        ScalarSetting single = singleSoupMaxHp;
        ScalarSetting twice = doubleSoupMaxHp;
        if (single.get() < twice.get()) twice.set(single.get());
        w.show(twice);
        w.heading("Delays");
        w.show(randomization);
        w.show(delayBefore);
        w.show(delayBetween);
        w.show(delayAfter);
        w.heading("Extra");
        w.show(dropBowls);
        w.show(overrideItem);
        if (overrideItem.get()) w.show(allowedItems);
    }
}
