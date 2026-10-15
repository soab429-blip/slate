package yt.ren.slate.feature.combat;

import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.CachedRandomSample;
import yt.ren.slate.aim.random.GaussianSampler;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.attack.AttackTargetCache;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.structs.Rotation;

public class AutoClicker extends Module {
    private final ChoiceSetting pattern = register("pattern", new ChoiceSetting("Click pattern", 2, 0, new String[]{"Jitter", "Butterfly"}, 0, 0, 0));
    private final ScalarSetting targetCps = register("target_cps", new ScalarSetting("Target CPS", "%0.1f CPS", 0.1f, 0.0f, 8.0f, 22.0f, 12.0f, 0.0f));
    private final BooleanSetting randomizeCps = register("randomize_cps", new BooleanSetting("Randomize", 1, 0));
    private final BooleanSetting exhaust = register("exhaust", new BooleanSetting("Simulate exhaust", 1, 0));
    private final BooleanSetting allowBlockBreak = register("allow_block_break", new BooleanSetting("Allow breaking blocks", 0, 0));
    private final BooleanSetting allowInventory = register("allow_inventory", new BooleanSetting("Click in inventory", 0, 0));
    private final BooleanSetting inventoryRandomize = register("inventory_randomize", new BooleanSetting("Randomize speed", 1, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireNotUsingItem = register("require_not_using_item", new BooleanSetting("Not using item", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;

    private Rotation previousRotation = new Rotation(0.0f, 0.0f);
    private int rightMouse;
    private double remaining;
    private double savedInterval;
    private final TimedNoise inventoryProbability = new TimedNoise();
    private int previousInventoryMouse;
    private final TimedNoise emptySlotProbability = new TimedNoise();
    private int hitKind;
    private final TimedNoise doubleProbability = new TimedNoise();
    private final TimedNoise amplitude = new TimedNoise();
    private final CachedRandomSample cpsSample = new CachedRandomSample();
    private int doubleClick;
    private final TimedNoise drift120 = new TimedNoise();
    private final TimedNoise drift30 = new TimedNoise();
    private final TimedNoise drift5 = new TimedNoise();
    private final TimedNoise exhaustProbability = new TimedNoise();
    private final TimedNoise exhaustCountProbability = new TimedNoise();
    private final CachedRandomSample exhaustSample = new CachedRandomSample();
    private int exhaustRemaining;
    private int lastInventorySlot = -1;

    public AutoClicker(ClientRuntime runtime) {
        super(ModuleId.AUTO_CLICKER, "Auto Clicker", "Clicks for you when holding down left-click.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }


    private float noise(TimedNoise state, float minimum, float maximum, double interval) {
        return state.sample(random, runtime.frameTime[0], minimum, maximum, interval);
    }

    private boolean chance(TimedNoise state, float minimum, float maximum, double interval) {
        return state.belowSample(random, runtime.frameTime[0], minimum, maximum, interval);
    }

    private void sendMouse(int pressed) {
        runtime.windowInput.postMouseButton(rightMouse ^ 1, pressed);
    }

    private float activity(ClientGameContext c) {
        Rotation rotation = RotationLifecycle.read(c.player);
        float value = Geometry.angleLength(rotation.subtract(previousRotation)) / 20.0f + 0.0f;
        AttackTargetCache target = c.targetCache;
        if (target.target == null || target.squaredDistance > 25.0) {
            value = (float) ((double) value + 0.08);
        }
        return value;
    }

    private void pause() {
        remaining = (inventoryRandomize.get() ? 0.05 : 0.001) + remaining;
    }

    private void inventoryClick(ClientGameContext c) {
        byte[] keys = c.keys;
        if (keys[1] != 1 || previousInventoryMouse != 1) {
            previousInventoryMouse = keys[1] == 1 ? 1 : 0;
            pause();
            return;
        }
        if (!allowInventory.get() || ((keys[0x62] & 1) == 0 && (keys[0x63] & 1) == 0)) {
            pause();
            return;
        }
        InventoryMenuScreen screen = inventoryScreen(c);
        PlayerInventory inventory = c.player.inventory;
        InventorySlot slot = screen.hoveredSlot;
        if (slot == null || inventory.cursorItem != null) {
            pause();
            return;
        }
        int id = slot.index;
        if (id == lastInventorySlot) {
            pause();
            return;
        }
        lastInventorySlot = id;
        ItemStack stack = slot.getItem();
        if (stack == null && chance(emptySlotProbability, 0.6f, 0.8f, 10.0)) {
            pause();
            return;
        }
        runtime.windowInput.postMouseButton(1, 1);
        if (!inventoryRandomize.get()) {
            remaining = 0.001 + remaining;
            return;
        }
        boolean longer = chance(inventoryProbability, 0.04f, 0.08f, 10.0);
        float delay = longer ? random.range(0.15f, 0.25f) : random.range(0.06f, 0.075f);
        remaining = (double) delay + remaining;
    }

    private static InventoryMenuScreen inventoryScreen(ClientGameContext c) {
        return c.client.screen instanceof InventoryMenuScreen ? (InventoryMenuScreen) c.client.screen : null;
    }

    void beginFrame(ClientGameContext c) {
        rightMouse = c.client.options.keyBindings[0].keyCode == -99 ? 1 : 0;
        if (c.focused != 1) {
            return;
        }
        previousRotation = RotationLifecycle.read(c.player);
        if (!runtime.module(ModuleId.NO_HIT_DELAY).isEnabled()) {
            c.client.attackCooldown = 0;
        }
    }

    boolean addExhaust(float[] interval, float motion) {
        int count = exhaustRemaining;
        if (count <= 0) {
            if (!exhaust.get()) {
                return false;
            }
            float minimum = (float) (0.02 + (double) motion);
            float maximum = (float) ((double) motion + 0.04);
            if (!chance(exhaustProbability, minimum, maximum, 300.0)) {
                return false;
            }
            float sample = random.range(0.0f, 1.0f);
            float probability = noise(exhaustCountProbability, 0.03f, 0.06f, 120.0);
            count = 3;
            if (!(probability > sample)) {
                count = 1 + (noise(exhaustCountProbability, 0.12f, 0.15f, 120.0) > sample ? 1 : 0);
            }
        }
        exhaustRemaining = count - 1;
        float sample = GaussianSampler.sampleCachedRandom(exhaustSample, random);
        double extra = (double) sample * 0.01 + 0.02;
        interval[0] = (float) ((double) interval[0] + extra);
        return true;
    }

    private void reset(double value) {
        remaining = value;
        lastInventorySlot = -1;
    }

    void update(FrameEvent event) {
        ClientGameContext c = (ClientGameContext) event.context;
        double timestep = event.elapsed;
        float speed = c.client.timer.tpsScale;
        double elapsed = (double) speed * timestep;
        double remainder = remaining - elapsed;
        remaining = Math.max(-0.2, remainder);
        if (!(remaining <= 0.0)) {
            return;
        }
        int input = c.keys[rightMouse + 1] & 0xFF;
        int synthetic = (input & 2) >> 1;
        do {
            if (c.focused == 0) {
                if ((synthetic & 1) != 0) {
                    sendMouse(~input & 1);
                }
                if (inventoryScreen(c) == null) {
                    reset(0.01);
                    return;
                }
                inventoryClick(c);
                return;
            }
            if (((input | synthetic) & 1) == 0) {
                reset(0.01);
                return;
            }
            boolean blocked = hitKind == 1 && allowBlockBreak.get();
            if (!blocked && requireWeapon.get()) {
                blocked = !HeldItem.holdingWeapon(runtime, c.player);
            }
            if (!blocked && requireNotUsingItem.get()) {
                blocked = c.player.hasItemInUse() || ((input & synthetic) & 1) != 0;
            } else if (!blocked && ((input & synthetic) & 1) != 0) {
                sendMouse(0);
                reset(0.0);
                return;
            }
            if (blocked) {
                if ((synthetic & 1) != 0) {
                    sendMouse(~input & 1);
                }
                reset(0.0);
                return;
            }
            if ((input & 1) != 0) {
                sendMouse(0);
                remaining = savedInterval + remaining;
            } else {
                sendMouse(1);
                float cps = targetCps.get();
                if (randomizeCps.get()) {
                    float scale = noise(amplitude, 0.2f, 0.3f, 30.0);
                    float slow = noise(drift120, -0.1f, 0.1f, 120.0);
                    float medium = noise(drift30, -0.1f, 0.1f, 30.0);
                    float fast = noise(drift5, -0.05f, 0.05f, 5.0);
                    cps = cps * (slow + 1.0f + medium + fast);
                    cps = cps * (GaussianSampler.sampleCachedRandom(cpsSample, random) * scale + 1.0f);
                }
                float[] interval = new float[1];
                if (pattern.index() == 1) {
                    if (doubleClick == 1) {
                        interval[0] = Float.intBitsToFloat(0x3c656042);
                        doubleClick = 0;
                    } else {
                        double reciprocal = 1.0 / (double) cps;
                        interval[0] = (float) (reciprocal + -(double) Float.intBitsToFloat(0x3c656042));
                        if (!addExhaust(interval, activity(c))) {
                            boolean probability = chance(doubleProbability, 0.94f, 0.97f, 30.0);
                            doubleClick = random.below(probability ? 1.0f : 0.0f) ? 1 : 0;
                        }
                    }
                } else {
                    interval[0] = 0.5f / cps;
                    addExhaust(interval, activity(c));
                }
                double saved = interval[0];
                remaining = remaining + saved;
                savedInterval = saved;
            }
            input ^= 1;
            synthetic ^= 1;
        } while (remaining <= 0.0);
    }

    void refreshHit(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        HitResult hit = c.client.crosshairTarget;
        hitKind = hit != null ? hit.type.ordinal() : 0;
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%s",
                pattern.choice(pattern.index()));
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> beginFrame((ClientGameContext) e.context));
        on(EventTypes.FRAME, this::update);
        on(EventTypes.UPDATE_ROTATION, e -> refreshHit((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("In-game");
        w.show(pattern);
        w.show(targetCps);
        w.show(randomizeCps);
        w.show(exhaust);
        w.show(allowBlockBreak);
        w.heading("Inventory");
        w.show(allowInventory);
        if (allowInventory.get())
            w.show(inventoryRandomize);
        w.heading("Conditions");
        w.show(requireWeapon);
        w.show(requireNotUsingItem);
    }
}
