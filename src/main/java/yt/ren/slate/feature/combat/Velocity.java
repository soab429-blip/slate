package yt.ren.slate.feature.combat;

import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.Targets;
import yt.ren.slate.input.PhysicalKeys;
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
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

public class Velocity extends Module {
    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 4, 0, new String[]{"Regular", "Jump", "Reduce", "Ignore"}, 0, 0, 0));
    private final ScalarSetting reduceChance = register("reduce_chance", new ScalarSetting("Chance", "%.0f%%", 1.0f, 0.0f, 0.0f, 100.0f, 100.0f, 0.0f));
    private final ScalarSetting fov = register("fov", new ScalarSetting("FOV", "%.0f\u00b0", 0.0f, 114.59156f, 0.0f, 3.1415927f, 3.1415927f, 0.0f));
    private final ScalarSetting reduceAmount = register("reduce_amount", new ScalarSetting("Reduce to", "%.0f%%", 0.01f, 100.0f, -1.0f, 1.0f, 1.0f, 0.0f));
    private final ScalarSetting reduceDelay = register("reduce_delay", new ScalarSetting("Reduce delay", "%.0f tick(s)", 1.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f));
    private final ScalarSetting jumpChance = register("jump_chance", new ScalarSetting("Chance", "%.0f%%", 1.0f, 0.0f, 0.0f, 100.0f, 100.0f, 0.0f));
    private final BooleanSetting reduceMultiplePerTick = register("reduce_multiple_per_tick", new BooleanSetting("Allow for double-clicks", 0, 0));
    private final ChoiceSetting ignoreVertical = register("ignore_vertical", new ChoiceSetting("Ignore vertical", 3, 0, new String[]{"Never", "Only in air", "Always"}, 0, 0, 0));
    private final ScalarSetting ignoreChance = register("ignore_chance", new ScalarSetting("Chance", "%.0f%%", 1.0f, 0.0f, 0.0f, 100.0f, 100.0f, 0.0f));
    private final BooleanSetting requireTarget = register("require_target", new BooleanSetting("Looking at player", 0, 0));
    private final BooleanSetting requireMouse = register("require_mouse", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireForward = register("require_forward", new BooleanSetting("Moving forward", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireSprintingJump = register("require_sprinting_jump", new BooleanSetting("Sprinting", 1, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private volatile int skipRestore;
    private int restoreAvailable;
    private Vec3 previousVelocity = new Vec3(0.0, 0.0, 0.0);
    private final TimedNoise probability = new TimedNoise();
    private volatile int playerId;
    private final AtomicInteger velocityReceived = new AtomicInteger();
    private Vec3 beforeAttack = new Vec3(0.0, 0.0, 0.0);
    private int attackReduced;
    private int reduceTicks;
    private int jumpOverridden;

    public Velocity(ClientRuntime runtime) {
        super(ModuleId.VELOCITY, "Velocity", "Reduces the amount of knockback you take.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> restoreVelocity(e.game()));
        on(EventTypes.PACKET_RECEIVE, this::observeVelocityPacket, -1000);
        on(EventTypes.MODULE_TOGGLE, this::handleVelocityToggle);
        on(EventTypes.CLICK_PRE, this::captureAttackVelocity);
        on(EventTypes.CLICK_POST, this::reduceAttackVelocity, 100);
        on(EventTypes.UPDATE_ROTATION, e -> updateVelocity(e.game()), 100);
        on(EventTypes.UPDATE_POST, e -> capturePreviousVelocity(e.game()));
    }

    @Override
    public String suffix() {
        if (mode.index() == 0) {
            return ValueFormatter.format("%.0f%%", (double) (reduceAmount.get() * 100.0f));
        }
        return mode.selected();
    }

    private boolean weapon(ClientGameContext c) {
        return HeldItem.holdingWeapon(runtime, c.player);
    }

    private static boolean attackMousePressed(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0;
    }

    private static Vec3 velocity(ClientGameContext c) {
        return new Vec3(c.player.velocityX, c.player.velocityY, c.player.velocityZ);
    }

    private static void writeVelocity(ClientGameContext c, Vec3 v) {
        c.player.velocityX = v.x;
        c.player.velocityY = v.y;
        c.player.velocityZ = v.z;
    }

    private boolean velocityChance(ScalarSetting setting) {
        float chance = setting.get();
        if (chance > 99.0f) {
            return true;
        }
        double scaled = (float) ((double) chance * 0.01);
        float low = (float) (-0.05 + scaled);
        float high = (float) (scaled + 0.05);
        return probability.belowSample(random, runtime.frameTime[0], low, high, 60.0);
    }

    private boolean velocityConditions(ClientGameContext c) {
        if (requireForward.get() && 0.0f >= c.player.forwardSpeed) {
            return false;
        }
        if (requireMouse.get() && !attackMousePressed(c)) {
            return false;
        }
        if (requireWeapon.get() && !weapon(c)) {
            return false;
        }
        return !requireTarget.get() || Targets.playerAhead(c);
    }

    void restoreVelocity(ClientGameContext c) {
        if (mode.index() != 3 || c.focused != 1) {
            return;
        }
        if ((skipRestore & 1) != 0) {
            skipRestore = 0;
            return;
        }
        if (restoreAvailable != 1 || !velocityConditions(c) || !velocityChance(ignoreChance)) {
            return;
        }
        Vec3 value = velocity(c);
        double x = previousVelocity.x;
        double y = value.y;
        int vertical = ignoreVertical.index();
        if (vertical == 2 || (vertical == 1 && !c.player.onGround)) {
            y = previousVelocity.y;
        }
        double z = previousVelocity.z;
        writeVelocity(c, new Vec3(x, y, z));
        restoreAvailable = 0;
    }

    void observeVelocityPacket(PacketEvent e) {
        if (e.action != 0) {
            return;
        }
        Object packet = e.packet;
        if (packet instanceof EntityVelocityS2CPacket) {
            if (((EntityVelocityS2CPacket) packet).id == playerId) {
                velocityReceived.getAndSet(1);
            }
        } else if (packet instanceof ExplosionS2CPacket) {
            ExplosionS2CPacket explosion = (ExplosionS2CPacket) packet;
            float x = explosion.playerVelocityX;
            float z = explosion.playerVelocityZ;
            float zSquared = z * z;
            float xSquared = x * x;
            velocityReceived.getAndSet((double) (xSquared + zSquared) > 0.05 ? 1 : 0);
        } else if (packet instanceof PlayerMoveS2CPacket) {
            skipRestore = 1;
        }
    }

    void handleVelocityToggle(ToggleEvent e) {
        if (e.module == this) {
            restoreAvailable = 0;
        }
    }

    void captureAttackVelocity(ClickEvent e) {
        if ((e.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = e.game();
        if (c.player != null) {
            beforeAttack = velocity(c);
        }
    }

    void reduceAttackVelocity(ClickEvent e) {
        if (mode.index() != 2
                || (attackReduced == 1 && !reduceMultiplePerTick.get())
                || (e.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = e.game();
        if (c.player == null) {
            return;
        }
        if (c.targetCache.slowdownMovesCloser != 1) {
            return;
        }
        if (requireForward.get() && 0.0f >= c.player.forwardSpeed) {
            return;
        }
        if (requireWeapon.get() && !weapon(c)) {
            return;
        }
        int ticks = c.player.ticks;
        int attack = c.player.lastAttackTime;
        if (ticks != attack) {
            return;
        }
        beforeAttack = new Vec3(beforeAttack.x * 0.6, beforeAttack.y, 0.6 * beforeAttack.z);
        writeVelocity(c, beforeAttack);
        attackReduced = 1;
    }

    void updateVelocity(ClientGameContext c) {
        attackReduced = 0;
        if (mode.index() == 2 || c.focused != 1) {
            return;
        }
        playerId = c.player.networkId;
        int selected = mode.index();
        if (selected == 1) {
            KeyBinding jump = c.client.options.keyBindings[6];
            if (jumpOverridden == 1) {
                jump.pressed = PhysicalKeys.pressed(jump);
                jumpOverridden = 0;
                return;
            }
            if (!velocityConditions(c) || !velocityChance(jumpChance)) {
                return;
            }
            if (requireSprintingJump.get() && !c.player.isSprinting()) {
                return;
            }
            if (!c.player.onGround) {
                return;
            }
            if (0.0 >= c.player.velocityY) {
                return;
            }
            StatusEffect jumpBoost = StatusEffect.BY_ID[8];
            if (c.player.getEffectInstance(jumpBoost) != null) {
                return;
            }
            if (jump.pressed) {
                return;
            }
            jump.pressed = true;
            jumpOverridden = 1;
            return;
        }
        if (selected != 0) {
            return;
        }
        if (reduceTicks <= 0) {
            if (!velocityReceived.compareAndSet(1, 0)) {
                return;
            }
            if (!velocityConditions(c) || !velocityChance(reduceChance)) {
                return;
            }
            Vec3 motion = velocity(c);
            double horizontal = CrtMath.hypot(motion.x, motion.z);
            if (horizontal == 0.0) {
                return;
            }
            float yaw = RotationLifecycle.read(c.player).yaw;
            double negativePi = -3.141592653589793;
            float radians = (float) ((double) yaw * negativePi / 180.0 + negativePi);
            float sine = ScalarTrig.sine(radians);
            float cosine = ScalarTrig.cosine(radians);
            double zProduct = (double) cosine * motion.z;
            double xProduct = (double) sine * motion.x;
            double angle = CrtMath.acos((xProduct + zProduct) / horizontal);
            if (!((double) fov.get() >= angle)) {
                return;
            }
            reduceTicks = Sse.cvttss2si(reduceDelay.get());
        }
        if (reduceTicks > 0 && --reduceTicks != 0) {
            return;
        }
        Vec3 motion = velocity(c);
        double amount = reduceAmount.get();
        writeVelocity(c, new Vec3(motion.x * amount, motion.y, amount * motion.z));
    }

    void capturePreviousVelocity(ClientGameContext c) {
        if (c.player != null) {
            previousVelocity = velocity(c);
            restoreAvailable = 1;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
        switch (mode.index()) {
            case 0:
                w.show(reduceChance);
                w.show(fov);
                w.show(reduceAmount);
                w.show(reduceDelay);
                break;
            case 1:
                w.show(jumpChance);
                break;
            case 2:
                w.show(reduceMultiplePerTick);
                break;
            case 3:
                w.show(ignoreVertical);
                w.show(ignoreChance);
                break;
            default:
                break;
        }
        w.heading("Conditions");
        if (mode.index() != 2) {
            w.show(requireTarget);
            w.show(requireMouse);
        }
        w.show(requireForward);
        w.show(requireWeapon);
        if (mode.index() == 1) w.show(requireSprintingJump);
    }
}
