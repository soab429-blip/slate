package yt.ren.slate.feature.combat;

import net.minecraft.client.TickTimer;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

public class TimerRange extends Module {
    private final ChoiceSetting modeSetting = register("mode", new ChoiceSetting("Mode", 2, 0, new String[]{"Regular", "Legit"}, 0, 0, 0));
    private final ScalarSetting minDist = register("min_dist", new ScalarSetting("Minimum distance", "%0.2f blocks", 0.0f, 0.0f, 2.0f, 2.9f, 2.7f, 0.0f));
    private final ScalarSetting durationSetting = register("duration", new ScalarSetting("Duration", "%0.0f ms", 1.0f, 50.0f, 1.0f, 10.0f, 3.0f, 0.0f));
    private final ScalarSetting durationLegit = register("duration_legit", new ScalarSetting("Duration", "%0.0f ms", 1.0f, 50.0f, 1.0f, 4.0f, 2.0f, 0.0f));
    private final ScalarSetting cooldownSetting = register("cooldown", new ScalarSetting("Cooldown", "%0.1f s", 0.0f, 0.0f, 0.1f, 5.0f, 2.0f, 0.0f));
    private final BooleanSetting blockWhileFrozen = register("block_while_frozen", new BooleanSetting("Block while frozen", 0, 0));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireNotUsingItem = register("require_not_using_item", new BooleanSetting("Not using item", 0, 0));

    private final ClientRuntime runtime;
    public int remaining;
    public int paused;
    private int eligible;
    private float forward;
    private float strafe;
    private volatile int holdPackets;
    private double cooldown;
    private double pauseRemaining;

    public TimerRange(ClientRuntime runtime) {
        super(ModuleId.TIMER_RANGE, "Timer Range", "Abuses timer lag to teleport you.", Category.COMBAT);
        this.runtime = runtime;
    }


    private int mode() {
        return modeSetting.index();
    }

    public boolean blocksUse() {
        return isEnabled() && (remaining > 0 || (paused & 1) != 0);
    }

    private static boolean attackMousePressed(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0;
    }

    static boolean holdingSword(PlayerEntity player) {
        PlayerInventory inventory = player.inventory;
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return false;
        }
        ItemStack stack = inventory.items[slot];
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item == null) {
            return false;
        }
        int index = Item.getId(item) - 0x10b;
        return Integer.compareUnsigned(index, 17) < 0 && ((0x10223 >>> index) & 1) != 0;
    }

    private static boolean visible(ClientGameContext c, Vec3 from, Vec3 to) {
        Vec3d start = new Vec3d(from.x, from.y, from.z);
        Vec3d end = new Vec3d(to.x, to.y, to.z);
        return c.world.rayTrace(start, end, false, true, true) == null;
    }

    private static float[] timerLook(Rotation angles) {
        double radians = 0.017453292519943295;
        float yaw = (float) (Double.longBitsToDouble(0xc00921fb60000000L) - (double) angles.yaw * radians);
        float cy = ScalarTrig.cosine(yaw);
        float sy = ScalarTrig.sine(yaw);
        float negativePitch = Float.intBitsToFloat(Float.floatToRawIntBits(angles.pitch) ^ 0x80000000);
        float pitch = (float) ((double) negativePitch * radians);
        float cp = ScalarTrig.cosine(pitch);
        float sp = ScalarTrig.sine(pitch);
        return new float[]{cy, sy, cp, sp};
    }

    private void captureInput(GameOptions options) {
        KeyBinding forwardKey = options.keyBindings[2];
        KeyBinding back = options.keyBindings[4];
        KeyBinding left = options.keyBindings[3];
        KeyBinding right = options.keyBindings[5];
        float f = forwardKey.pressed ? 1.0f : 0.0f;
        if (back.pressed) {
            f = f + -1.0f;
        }
        float side = left.pressed ? 1.0f : 0.0f;
        if (right.pressed) {
            side = side + -1.0f;
        }
        forward = f;
        strafe = side;
    }

    private void pause(ClientGameContext c) {
        c.client.timer.tpsScale = 0.0f;
        paused = 1;
        if (mode() == 1) {
            holdPackets = 1;
            return;
        }
        if (blockWhileFrozen.get() && holdingSword(c.player)) {
            KeyBinding binding = c.client.options.keyBindings[1];
            binding.pressed = true;
            binding.clickCount = binding.clickCount + 1;
        }
    }

    void updateFrame(ClientGameContext c) {
        if (remaining > 0) {
            GameOptions options = c.client.options;
            remaining = remaining - 1;
            if (remaining == 0 && mode() == 0) {
                pause(c);
            }
            if (c.focused == 1 && c.targetCache.target != null) {
                HitResult hit = c.client.crosshairTarget;
                if (hit != null && hit.type.ordinal() == 2) {
                    c.client.attackCooldown = 0;
                    KeyBinding attack = options.keyBindings[0];
                    attack.clickCount = attack.clickCount + 1;
                }
            }
            return;
        }
        eligible = 0;
        if (c.focused != 1) {
            return;
        }
        PlayerEntity target = c.targetCache.target;
        if (target == null || 3.0 > c.targetCache.squaredDistance) {
            return;
        }
        if (!c.player.onGround) {
            return;
        }
        if (target.damagedTimer > 1) {
            return;
        }
        if (requireMousePressed.get() && !attackMousePressed(c)) {
            return;
        }
        if (requireWeapon.get()
                && !HeldItem.holdingWeapon(runtime, c.player)) {
            return;
        }
        if (requireNotUsingItem.get() && c.player.hasItemInUse()) {
            return;
        }
        if (c.player.velocityY > 0.0) {
            return;
        }
        GameOptions options = c.client.options;
        if (options.keyBindings[6].pressed) {
            return;
        }
        Vec3 from = new Vec3(c.player.x, c.player.y, c.player.z).addStandingEyeHeight();
        RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) target;
        Vec3 to = new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ).addStandingEyeHeight();
        if (!visible(c, from, to)) {
            return;
        }
        captureInput(options);
        eligible = forward > 0.0f ? 1 : 0;
    }

    void updatePrediction(FrameEvent e) {
        ClientGameContext c = e.game();
        TickTimer timer = c.client.timer;
        if (paused == 1) {
            pauseRemaining = pauseRemaining - e.elapsed;
            if (pauseRemaining > 0.0) {
                timer.tpsScale = 0.0f;
                return;
            }
            c.client.timer.tpsScale = 1.0f;
            paused = 0;
            holdPackets = 0;
            int currentMode = mode();
            if (((currentMode != 1 ? 1 : 0) & (blockWhileFrozen.get() ? 1 : 0)) == 1) {
                if (holdingSword(c.player)) {
                    c.client.options.keyBindings[1].pressed = false;
                }
                currentMode = mode();
            }
            if (currentMode == 1) {
                timer.tickDelta = (float) remaining;
            }
        }
        if (cooldown > 0.0) {
            cooldown = cooldown - e.elapsed;
            return;
        }
        if (eligible != 1 || c.focused != 1) {
            return;
        }
        PlayerEntity target = c.targetCache.target;
        if (target == null) {
            return;
        }
        Vec3 velocity = new Vec3(c.player.velocityX, c.player.velocityY, c.player.velocityZ);
        Vec3 position = new Vec3(c.player.x, c.player.y, c.player.z).addStandingEyeHeight();
        Vec3 targetPosition = new Vec3(target.x, target.y, target.z);
        RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) target;
        Vec3 targetDestination = new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ);
        int targetSteps = remote.lerpSteps;
        Rotation rotation = RotationLifecycle.read(c.player);
        float[] factors = timerLook(rotation);
        float duration = (mode() == 0 ? durationSetting : durationLegit).get();
        int limit = Sse.cvttss2si(duration);
        if (limit <= 0) {
            return;
        }
        float negativeCp = Float.intBitsToFloat(Float.floatToRawIntBits(factors[2]) ^ 0x80000000);
        Vec3 look = new Vec3(factors[1] * negativeCp, factors[3], factors[0] * negativeCp);
        int negativeLimit = -limit;
        int step = 1;
        for (; ; ) {
            Bounds box = new Bounds(targetPosition.subtract(new Vec3(0.4, 0.1, 0.4)),
                    targetPosition.add(new Vec3(0.4, 1.9, 0.4)));
            Vec3 inverse = Geometry.reciprocalComponents(look.scale(3.0));
            if (Geometry.segmentIntersectsBounds(box, position, inverse)) {
                double square = Geometry.distanceSquared(position, Geometry.nearestPoint(box, position));
                if (square > 0.0) {
                    float range = minDist.get();
                    if ((double) (range * range) > square || step == 0xffffffff) {
                        return;
                    }
                    float count = (float) step;
                    if (mode() == 1) {
                        pause(c);
                    } else {
                        timer.tickDelta = count;
                    }
                    remaining = step;
                    float missing = count - e.partialTick;
                    pauseRemaining = (double) missing * 0.05;
                    cooldown = cooldownSetting.get();
                    return;
                }
            }
            float factor = c.player.speed;
            velocity = velocity.add(MathUtil.movementAcceleration(rotation, forward, strafe, factor));
            position = position.add(velocity);
            velocity = velocity.scale(0.546);
            if (mode() == 1) {
                double speedHorizontal = CrtMath.hypot(velocity.x, velocity.z);
                Vec3 delta = position.subtract(targetDestination);
                double distanceHorizontal = CrtMath.hypot(delta.x, delta.z);
                Vec3 motion = delta.scale(speedHorizontal / distanceHorizontal);
                motion = new Vec3(motion.x, 0.0, motion.z);
                targetDestination = targetDestination.add(motion);
            }
            if (targetSteps > 0) {
                Vec3 delta = targetDestination.subtract(targetPosition);
                double stepFactor = 1.0 / (double) targetSteps;
                targetPosition = targetPosition.add(delta.scale(stepFactor));
                --targetSteps;
            }
            int stop = negativeLimit + step + 1;
            ++step;
            if (stop == 1) {
                return;
            }
        }
    }

    void filterPacket(PacketEvent e) {
        if ((holdPackets & 1) != 0) {
            e.action = 1;
        }
    }

    void handleToggle(ToggleEvent e, Module descriptor) {
        if (e.module != descriptor || (e.state & 0xff) != 0) {
            return;
        }
        ClientGameContext c = (ClientGameContext) e.context;
        c.client.timer.tpsScale = 1.0f;
        paused = 0;
        remaining = 0;
        holdPackets = 0;
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%s", modeSetting.choice(mode()));
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updateFrame((ClientGameContext) e.context), -100);
        on(EventTypes.FRAME, e -> updatePrediction(e));
        on(EventTypes.PACKET_SEND, e -> filterPacket(e));
        on(EventTypes.MODULE_TOGGLE, e -> handleToggle(e, this));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(modeSetting);
        w.show(minDist);
        w.show(modeSetting.is(0) ? durationSetting : durationLegit);
        w.show(cooldownSetting);
        if (modeSetting.is(0)) w.show(blockWhileFrozen);
        w.heading("Conditions");
        w.show(requireMousePressed);
        w.show(requireWeapon);
        w.show(requireNotUsingItem);
    }
}
