package yt.ren.slate.feature.combat;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimFrameDriver;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.event.Events.RotationRequestEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.structs.Rotation;

public class KnockbackDisplace extends Module {
    private final ChoiceSetting direction = register("direction", new ChoiceSetting("Direction", 5, 0, new String[]{"Left", "Right", "Random", "Strafe", "Strafe (inverted)"}, 0, 0, 0));
    private final BooleanSetting useLastStrafe = register("use_last_strafe", new BooleanSetting("Use last strafe direction", 0, 0));
    private final ScalarSetting angle = register("angle", new ScalarSetting("Angle", "%.0f\u00b0", 0.0f, 0.0f, 45.0f, 180.0f, 90.0f, 0.0f));
    private final ScalarSetting cooldown = register("cooldown", new ScalarSetting("Cooldown", "%.0f ms", 0.0f, 50.0f, 0.0f, 40.0f, 5.0f, 0.0f));
    private final BooleanSetting hideRotation = register("hide_rotation", new BooleanSetting("Hide rotation from players", 0, 0));
    private final BooleanSetting requireNoKb = register("require_no_kb", new BooleanSetting("Not taking knockback", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting requireKbEnchant = register("require_kb_enchant", new BooleanSetting("Knockback enchantment", 0, 0));
    private final BooleanSetting requireFacingAway = register("require_facing_away", new BooleanSetting("Target facing away", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private TimerRange timerRange;
    private volatile int holdPackets;
    private int active;
    private int previousActive;
    private int targetSeen;
    private int attackTicks;
    private int cooldownTicks;
    private Rotation originalRotation = new Rotation(0.0f, 0.0f);
    private int strafeDirection;

    public KnockbackDisplace(ClientRuntime runtime) {
        super(ModuleId.KNOCKBACK_DISPLACE, "Knockback Displace", "Allows dealing knockback at a different angle.", Category.COMBAT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        timerRange = (TimerRange) runtime.module(ModuleId.TIMER_RANGE);
        on(EventTypes.PACKET_SEND, this::filterDisplacePacket);
        on(EventTypes.ROTATION_REQUEST, this::filterDisplaceRotation, -1000);
        on(EventTypes.CLICK_PRE, this::beforeDisplaceAttack, 1000);
        on(EventTypes.CLICK_POST, this::afterDisplaceAttack);
        on(EventTypes.UPDATE_POST, e -> updateDisplace(e.game()), 1000);
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.0f°", (double) angle.get());
    }

    private static float magnitude(float value) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(value) & 0x7fffffff);
    }

    void filterDisplacePacket(PacketEvent e) {
        if ((holdPackets & 1) != 0) {
            e.action = 1;
        }
    }

    void filterDisplaceRotation(RotationRequestEvent e) {
        if (active == 0 && previousActive == 0) {
            return;
        }
        e.remap = 0;
    }

    private static boolean targetFacingAway(Rotation original, RemoteClientPlayerEntity target) {
        double serverYaw = target.lerpYaw;
        return magnitude(Geometry.wrapYaw((float) serverYaw - original.yaw)) < 100.0f;
    }

    private int selectDirection() {
        switch (direction.index()) {
            case 0:
                return -1;
            case 1:
                return 1;
            case 2:
                return random.below(0.5f) ? 1 : -1;
            case 3:
                return strafeDirection;
            case 4:
                return -strafeDirection;
            default:
                return 0;
        }
    }

    private void applySharedRotation(ClientGameContext c) {
        RotationLifecycle.applyRotation(c.player, runtime.sharedRotation.accumulator, runtime.rotation.step, 0);
    }

    void beforeDisplaceAttack(ClickEvent e) {
        if (active == 1) {
            e.cancelled = 1;
            return;
        }
        if ((e.left & 0xff) != 1) {
            return;
        }
        ClientGameContext c = e.game();
        HitResult hit = c.client.crosshairTarget;
        if (hit == null || hit.type.ordinal() != 2) {
            return;
        }
        Entity entity = hit.entity;
        if (!(entity instanceof RemoteClientPlayerEntity)) {
            return;
        }
        RemoteClientPlayerEntity target = (RemoteClientPlayerEntity) entity;
        if (PlayerTags.has(target, 7)) {
            return;
        }
        targetSeen = 1;
        if (previousActive != 0 || Integer.compareUnsigned(attackTicks, 9) < 0
                || cooldown.get() > (float) (cooldownTicks & 0xFFFFFFFFL)
                || timerRange.blocksUse()) {
            return;
        }
        if (requireNoKb.get() && c.targetCache.slowdownMovesCloser != 0) {
            return;
        }
        int[] tags = new int[1];
        int allowed = runtime.module(Weapons.class).allows(c.player, tags) ? 1 : 0;
        if (requireWeapon.get() && allowed == 0) {
            return;
        }
        if ((tags[0] & 2) == 0
                && (requireKbEnchant.get() || !c.player.isSprinting())) {
            return;
        }
        originalRotation = RotationLifecycle.read(c.player);
        if (requireFacingAway.get() && !targetFacingAway(originalRotation, target)) {
            return;
        }
        int direction = selectDirection();
        if (direction == 0 || !AimFrameDriver.claimRotationPriority(runtime.sharedRotation, 200)) {
            return;
        }
        runtime.rotation.finishRotationUpdate(c, 1, 0, 0, originalRotation);
        active = 1;
        boolean positivePitch = random.below(0.5f);
        float sign = (float) direction;
        float degrees = angle.get();
        float yaw = (degrees - random.range(0.0f, 10.0f)) * sign;
        float pitch = random.range(10.0f, 20.0f);
        if (!positivePitch) {
            pitch = Float.intBitsToFloat(Float.floatToRawIntBits(pitch) ^ 0x80000000);
        }
        RotationLifecycle.addRotationDelta(runtime.sharedRotation, new Rotation(yaw, pitch));
        applySharedRotation(c);
        if (hideRotation.get()) {
            holdPackets = 1;
        }
        e.cancelled = 1;
        cooldownTicks = 0;
    }

    void afterDisplaceAttack(ClickEvent e) {
        if ((e.left & 0xff) == 1 && Integer.compareUnsigned(attackTicks, 9) > 0 && (targetSeen & 1) != 0) {
            attackTicks = 0;
        }
    }

    void updateDisplace(ClientGameContext c) {
        ++attackTicks;
        ++cooldownTicks;
        previousActive = active;
        targetSeen = 0;
        if (c.focused == 1) {
            float strafe = c.player.sidewaysSpeed;
            int dir = (strafe > 0.0f ? 1 : 0) - (0.0f > strafe ? 1 : 0);
            if (!useLastStrafe.get() || dir != 0) {
                strafeDirection = dir;
            }
            if (active != 1) {
                holdPackets = previousActive != 0 ? (hideRotation.get() ? 1 : 0) : 0;
                return;
            }
            if (AimFrameDriver.claimRotationPriority(runtime.sharedRotation, 200)) {
                Rotation current = RotationLifecycle.read(c.player);
                Rotation delta = runtime.sharedRotation.accumulator.delta;
                RotationLifecycle.addRotationDelta(runtime.sharedRotation,
                        originalRotation.subtract(current.add(delta)));
                applySharedRotation(c);
                c.client.attackCooldown = 0;
                KeyBinding attack = c.client.options.keyBindings[0];
                attack.clickCount = attack.clickCount + 1;
            }
        }
        active = 0;
        holdPackets = previousActive != 0 ? (hideRotation.get() ? 1 : 0) : 0;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(direction);
        if (Integer.compareUnsigned(direction.index() - 3, 2) < 0)
            w.show(useLastStrafe);
        w.show(cooldown);
        w.show(angle);
        w.show(hideRotation);
        w.heading("Condition");
        w.show(requireNoKb);
        w.show(requireWeapon);
        w.show(requireKbEnchant);
        w.show(requireFacingAway);
    }
}
