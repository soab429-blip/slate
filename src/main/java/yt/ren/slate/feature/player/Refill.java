package yt.ren.slate.feature.player;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.CachedRandomSample;
import yt.ren.slate.aim.random.GaussianSampler;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ControllerEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.structs.Rotation;

public class Refill extends Module {
    private final BooleanSetting refillPotion = register("refill_potion", new BooleanSetting("Potions", 1, 0));
    private final BooleanSetting refillPotionNonHealth = register("refill_potion_non_health", new BooleanSetting("Allow non-health potions", 0, 0));
    private final BooleanSetting refillSoup = register("refill_soup", new BooleanSetting("Soups", 1, 0));
    private final ChoiceSetting simulateButton = register("simulate_button", new ChoiceSetting("Simulate button", 3, 0, new String[]{"Left", "Right", "Left + right"}, 1, 0, 0));
    private final ScalarSetting delayAfterOpen = register("delay_after_open", new ScalarSetting("Delay after open", "%.0f ms", 0.0f, 50.0f, 0.0f, 10.0f, 2.0f, 0.0f));
    private final ScalarSetting delayBeforeClose = register("delay_before_close", new ScalarSetting("Delay before close", "%.0f ms", 0.0f, 50.0f, 0.0f, 5.0f, 1.0f, 0.0f));
    private final ScalarSetting speedSetting = register("speed", new ScalarSetting("Speed", "%.1fx", 0.0f, 0.0f, 1.0f, 10.0f, 2.0f, 0.0f));
    private final BooleanSetting smartSpeed = register("smart_speed", new BooleanSetting("Smart speed", 1, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;

    private int phase;
    private final TimedNoise openingDelay = new TimedNoise();
    private final TimedNoise closingDelay = new TimedNoise();
    private final TimedNoise speed = new TimedNoise();
    private final TimedNoise slowProbability = new TimedNoise();
    private final CachedRandomSample speedSample = new CachedRandomSample();
    private float remaining;
    private final int[] slots = new int[27];
    private int cursor;
    private float heading;
    private int selectedSlot;
    private int nextButton;
    private final TimedNoise buttonProbability = new TimedNoise();
    private final TimedNoise emptyProbability = new TimedNoise();
    private final TimedNoise alternateProbability = new TimedNoise();

    public Refill(ClientRuntime runtime) {
        super(ModuleId.REFILL, "Refill", "Automatically moves items into your hotbar.", Category.UTILITY);
        traits(Trait.NO_TOGGLE, Trait.HIDDEN, Trait.NO_HOLD);
        this.runtime = runtime;
        this.random = runtime.random;
    }


    private boolean chance(TimedNoise state, float minimum, float maximum, double interval) {
        return state.belowSample(random, runtime.frameTime[0], minimum, maximum, interval);
    }

    private static InventoryMenuScreen screen(ClientGameContext c) {
        return c.client.screen instanceof InventoryMenuScreen ? (InventoryMenuScreen) c.client.screen : null;
    }

    private static boolean hotbarHasSpace(ClientGameContext c) {
        if (c.player == null) {
            return false;
        }
        PlayerInventory inventory = c.player.inventory;
        for (int slot = 0; slot != 9; ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null) {
                return true;
            }
            Item item = stack.getItem();
            if (item == null || Item.getId(item) == 0) {
                return true;
            }
        }
        return false;
    }

    private void addDelay(ScalarSetting setting, TimedNoise noise) {
        double base = setting.get();
        double varied = noise.sample(random, runtime.frameTime[0], 0.0f, 2.0f, 30.0);
        double difference = base - varied;
        double delay = difference > 0.0 ? difference : 0.0;
        remaining = (float) ((double) remaining + delay);
    }

    private void beginClose() {
        addDelay(delayBeforeClose, closingDelay);
        phase = 4;
    }

    private float clickSpeed() {
        float base = speedSetting.get();
        float variation = speed.sample(random, runtime.frameTime[0], 0.8f, 1.2f, 30.0);
        float varied = variation * base;
        float sample = GaussianSampler.sampleCachedRandom(speedSample, random);
        float value = (float) ((double) varied * ((double) sample * 0.3 + 1.0));
        if (chance(slowProbability, 0.04f, 0.08f, 30.0)) {
            value = value / random.range(1.4f, 1.8f);
        }
        return value;
    }

    private void captureSlots(ClientGameContext c) {
        PlayerInventory inventory = c.player.inventory;
        for (int index = 0; index != 27; ++index) {
            ItemStack stack = inventory.getItem(index + 9);
            int kind = 0;
            if (stack != null) {
                Item item = stack.getItem();
                if (item != null) {
                    int id = Item.getId(item);
                    if (id != 0) {
                        kind = 1;
                        if (id == 0x11a) {
                            kind = ((refillSoup.get() ? 1 : 0) + 1) & 0xFF;
                        } else if (id == 0x175 && refillPotion.get()) {
                            int damage = stack.metadata;
                            if ((damage & 0x4000) != 0) {
                                kind = (damage & 15) == 5 ? 2 : ((refillPotionNonHealth.get() ? 1 : 0) + 1) & 0xFF;
                            }
                        }
                    }
                }
            }
            slots[index] = kind;
        }
    }

    private int selectSlot(int from, float currentHeading, float[] cost) {
        Rotation origin = new Rotation((float) (from % 9), (float) (from / 9));
        float bestCost = Float.MAX_VALUE;
        float priorCost = bestCost;
        float emptyCost = bestCost;
        float bestAngle = 0.0f;
        float priorAngle = 0.0f;
        float emptyAngle = 0.0f;
        int best = -1;
        int prior = -1;
        int empty = -1;
        for (int index = 0; index != 27; ++index) {
            int kind = slots[index];
            if (kind != 2 && (kind != 0 || random.below(0.5f))) {
                continue;
            }
            Rotation candidate = new Rotation((float) (index % 9), (float) (index / 9));
            float distance = Geometry.angleLength(origin.subtract(candidate));
            float angle = CrtMath.atan2f(candidate.pitch - origin.pitch, candidate.yaw - origin.yaw);
            float angularCost = Floats.magnitude(Geometry.wrapYaw(angle - currentHeading)) * 0.5f;
            float score = distance * 1.2f + angularCost;
            if (kind == 0) {
                if (!(emptyCost < score)) {
                    emptyAngle = angle;
                }
                if (!(score > emptyCost)) {
                    empty = index;
                }
                emptyCost = Floats.min(emptyCost, score);
            } else {
                float previousAngle = bestAngle;
                if (!(score > bestCost)) {
                    priorCost = bestCost;
                    prior = best;
                    best = index;
                    bestAngle = angle;
                }
                if (!(bestCost < score)) {
                    priorAngle = previousAngle;
                }
                bestCost = Floats.min(bestCost, score);
            }
        }
        if (bestCost > emptyCost && chance(emptyProbability, 0.05f, 0.1f, 20.0)) {
            cost[0] = emptyCost;
            heading = emptyAngle;
            return empty;
        }
        if (prior != -1 && (double) bestCost * 1.5 > (double) priorCost
                && chance(alternateProbability, 0.05f, 0.1f, 10.0)) {
            cost[0] = priorCost;
            heading = priorAngle;
            return prior;
        }
        cost[0] = bestCost;
        heading = bestAngle;
        return best;
    }

    private void finish(ClientGameContext c, Module descriptor) {
        c.registry.queueModuleChange(descriptor, 0);
        InventoryMenuScreen current = screen(c);
        if (current != null) {
            ((Screen) current).keyPressed((char) 0, 1);
        }
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.module == descriptor && event.state == 1) {
            phase = 0;
        }
    }

    void update(ControllerEvent event, Module descriptor) {
        ClientGameContext c = event.game();
        InventoryMenuScreen current = screen(c);
        if (phase != 0) {
            if (current == null || !(current instanceof SurvivalInventoryScreen)) {
                finish(c, descriptor);
                return;
            }
            event.drain = 1;
        }
        if (remaining > 0.0f) {
            remaining = remaining + -1.0f;
            return;
        }
        if (!(remaining <= 0.0f)) {
            return;
        }
        while (true) {
            if (phase == 4) {
                finish(c, descriptor);
                return;
            }
            if (!hotbarHasSpace(c)) {
                beginClose();
                if (remaining <= 0.0f) {
                    finish(c, descriptor);
                }
                return;
            }
            switch (phase) {
                case 0: {
                    phase = 1;
                    if (current != null) {
                        return;
                    }
                    captureSlots(c);
                    float[] cost = new float[1];
                    if (selectSlot(cursor, heading, cost) == -1) {
                        finish(c, descriptor);
                        return;
                    }
                    KeyBinding binding = c.client.options.keyBindings[10];
                    binding.clickCount = binding.clickCount + 1;
                    return;
                }
                case 1:
                    cursor = Sse.cvttss2si(random.range(0.0f, 27.0f));
                    heading = random.range(0.0f, Float.intBitsToFloat(0x40490fdb));
                    phase = 2;
                    addDelay(delayAfterOpen, openingDelay);
                    break;
                case 2: {
                    float[] cost = new float[1];
                    selectedSlot = selectSlot(cursor, heading, cost);
                    if (selectedSlot == -1) {
                        beginClose();
                        return;
                    }
                    float clickSpeed = clickSpeed();
                    float distance = smartSpeed.get() ? cost[0] : 1.0f;
                    remaining = distance / clickSpeed + remaining;
                    cursor = selectedSlot;
                    phase = 3;
                    break;
                }
                case 3: {
                    int button = simulateButton.index();
                    if (button == 2) {
                        button = nextButton;
                        if (chance(buttonProbability, 0.9f, 1.0f, 10.0)) {
                            nextButton = nextButton == 0 ? 1 : 0;
                        }
                    }
                    current.clickSlot(null, selectedSlot + 9, button, 1);
                    slots[selectedSlot] = 0;
                    phase = 2;
                    break;
                }
                default:
                    return;
            }
            if (!(remaining <= 0.0f)) {
                return;
            }
        }
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.CONTROLLER, e -> update(e, this));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Items");
        w.show(refillPotion);
        if (refillPotion.get())
            w.show(refillPotionNonHealth);
        w.show(refillSoup);
        w.heading("Clicks");
        w.show(simulateButton);
        w.show(delayAfterOpen);
        w.show(delayBeforeClose);
        w.show(speedSetting);
        w.show(smartSpeed);
    }
}
