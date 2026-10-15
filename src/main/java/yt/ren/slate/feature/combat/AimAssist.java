package yt.ren.slate.feature.combat;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimAdjuster;
import yt.ren.slate.aim.AimFrameDriver;
import yt.ren.slate.aim.AimSettings;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.aim.rotation.SharedRotation;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FrameEvent;
import yt.ren.slate.event.Events.RotationRequestEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

import java.util.List;

public class AimAssist extends Module {
    private final ChoiceSetting modeSetting = register("mode", new ChoiceSetting("Mode", 4, 0, new String[]{"Regular", "Linear", "Lock-on", "Silent"}, 0, 0, 0));
    private final ScalarSetting horSpeed = register("hor_speed", new ScalarSetting("Horizontal speed", "%.1f", 0.1f, 0.0f, 0.0f, 30.0f, 15.0f, 0.0f));
    private final ScalarSetting verSpeed = register("ver_speed", new ScalarSetting("Vertical speed", "%.1f", 0.1f, 0.0f, 0.0f, 30.0f, 0.0f, 0.0f));
    private final BooleanSetting lockOnVertical = register("lock_on_vertical", new BooleanSetting("Vertical", 0, 0));
    private final BooleanSetting silentMoveCorrect = register("silent_move_correct", new BooleanSetting("Keep move direction", 1, 0));
    private final BooleanSetting silentIgnoreManualAim = register("silent_ignore_manual_aim", new BooleanSetting("Ignore manual aim", 0, 0));
    private final ScalarSetting predictSetting = register("predict", new ScalarSetting("Predict", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting aimRandomization = register("aim_randomization", new ScalarSetting("Randomization", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.5f, 0.0f));
    private final ScalarSetting horHitboxCorrect = register("hor_hitbox_correct", new ScalarSetting("Horizontal", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting verHitboxCorrect = register("ver_hitbox_correct", new ScalarSetting("Vertical", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 1.0f, 0.0f));
    private final ChoiceSetting modeTarget = register("mode_target", new ChoiceSetting("Mode", 2, 0, new String[]{"Single", "Switch"}, 0, 0, 0));
    private final ChoiceSetting sortBy = register("sort_by", new ChoiceSetting("Sort by", 4, 0, new String[]{"Distance", "Health", "Hurt time", "Aim angle"}, 0, 0, 0));
    private final BooleanSetting allowExceedFov = register("allow_exceed_fov", new BooleanSetting("Increased FOV while locked", 0, 0));
    private final RangeSetting range = register("range", new RangeSetting("Range", "%0.1f blocks", "%0.1f - %0.1f blocks", 0.1f, 0.0f, 0.0f, 10.0f, 1.0f, 4.0f, 0.0f, 0.0f));
    private final RangeSetting fov = register("fov", new RangeSetting("FOV", "%0.0f\u00b0", "%0.0f - %0.0f\u00b0", 0.5f, 2.0f, 0.0f, 180.0f, 4.0f, 60.0f, 0.0f, 0.0f));
    private final BooleanSetting requireSprint = register("require_sprint", new BooleanSetting("Sprinting", 0, 0));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireMouseMoved = register("require_mouse_moved", new BooleanSetting("Mouse moved", 0, 0));
    private final BooleanSetting disableOnBlockBreak = register("disable_on_block_break", new BooleanSetting("Disable while breaking blocks", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));
    private final BooleanSetting ignoreInvis = register("ignore_invis", new BooleanSetting("Ignore invisibles", 1, 0));
    private final BooleanSetting requireLineOfSight = register("require_line_of_sight", new BooleanSetting("Not behind blocks", 1, 0));

    private final ClientRuntime runtime;
    private final AimSettings adjustment = new AimSettings();

    public int active;
    public Vec3 previousTarget = Vec3.ZERO;
    public Vec3 currentTarget = Vec3.ZERO;
    public int secondaryRotation;
    private int inputDelay;
    private long tieCounter;

    public AimAssist(ClientRuntime runtime) {
        super(ModuleId.AIM_ASSIST, "Aim Assist", "Pulls your crosshair towards other players.", Category.COMBAT);
        this.runtime = runtime;
    }


    private int mode() {
        return modeSetting.index();
    }

    private AimSettings adjustmentSettings() {
        adjustment.mode = mode();
        adjustment.deadzone = fov.low();
        adjustment.horizontalSpeed = horSpeed.get();
        adjustment.verticalSpeed = verSpeed.get();
        adjustment.verticalInModeTwo = lockOnVertical.get();
        adjustment.limitToMouseDelta = requireMouseMoved.get();
        return adjustment;
    }

    private void adjust(Rotation difference, float timestep) {
        AimAdjuster.adjustAim(adjustmentSettings(), runtime.aimAdjustment, runtime.random, runtime.frameTime[0],
                difference, timestep, runtime.sharedRotation.accumulator);
    }

    private static Rotation wrappedDifference(Rotation left, Rotation right) {
        Rotation result = left.subtract(right);
        return new Rotation(Geometry.wrapYaw(result.yaw), result.pitch);
    }

    private static Vec3 position(PlayerEntity entity) {
        return new Vec3(entity.x, entity.y, entity.z);
    }

    void frame(FrameEvent event) {
        ClientGameContext c = (ClientGameContext) event.context;
        if (c.focused != 1) {
            return;
        }
        LocalClientPlayerEntity player = c.player;
        SharedRotation rotation = runtime.sharedRotation;
        if (active == 0) {
            if (secondaryRotation != 1) {
                return;
            }
            if (rotation.referenceAvailable && AimFrameDriver.claimRotationPriority(rotation, 100)) {
                Rotation current = RotationLifecycle.read(player).add(rotation.accumulator.delta);
                Rotation difference = wrappedDifference(rotation.reference, current);
                if (Geometry.angleLength(difference) < 10.0f) {
                    secondaryRotation = 0;
                }
                adjust(difference, (float) event.elapsed);
                return;
            }
            secondaryRotation = 0;
            return;
        }
        if (!AimFrameDriver.claimRotationPriority(rotation, 100)) {
            return;
        }
        if (secondaryRotation == 0 && mode() == 3) {
            secondaryRotation = 1;
            if (!rotation.referenceAvailable) {
                return;
            }
        } else if (secondaryRotation != 0 && !rotation.referenceAvailable) {
            return;
        }
        float partial = event.partialTick;
        Vec3 previous = new Vec3(player.lastX, player.lastY, player.lastZ);
        Vec3 now = position(player);
        Vec3 eye = previous.add(now.subtract(previous).scale(partial)).addStandingEyeHeight();
        Vec3 target = previousTarget.interpolate(currentTarget, event.partialTick);
        Rotation desired = Geometry.directionBetween(eye, target);
        Rotation current = RotationLifecycle.read(player).add(rotation.accumulator.delta);
        Rotation difference = wrappedDifference(desired, current);
        if (mode() == 3 && !silentIgnoreManualAim.get()) {
            Rotation towardsReference = wrappedDifference(rotation.reference, current);
            Rotation fromReference = wrappedDifference(desired, rotation.reference);
            if (Geometry.angleLength(towardsReference) < 10.0f
                    && Geometry.angleLength(fromReference) < Geometry.angleLength(difference)) {
                RotationLifecycle.addRotationDelta(rotation, towardsReference);
                return;
            }
        }
        adjust(difference, (float) event.elapsed);
    }

    void toggle(ToggleEvent event, Module self) {
        if (event.module == self && event.state == 0) {
            runtime.aimTargetReference = null;
            active = 0;
        }
    }

    void flags(RotationRequestEvent event) {
        if (secondaryRotation == 1) {
            event.enabled = 1;
            if (silentMoveCorrect.get()) {
                event.remap = 1;
            }
        }
    }

    private void release() {
        runtime.aimTargetReference = null;
        active = 0;
    }

    private static int attackInput(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0 ? 1 : 0;
    }

    void acquire(ClientGameContext c) {
        runtime.aimAdjustment.mouseDelta = new Rotation(0.0f, 0.0f);
        if (c.focused != 1) {
            release();
            return;
        }
        LocalClientPlayerEntity player = c.player;
        if (requireSprint.get() && mode() != 3 && !player.isSprinting()) {
            active = 0;
            return;
        }
        int input = attackInput(c);
        if (requireMousePressed.get()) {
            if (input == 0) {
                inputDelay = ((runtime.random.below(0.5f) ? 1 : 0) + 1) & 0xff;
                release();
                return;
            }
            if (inputDelay != 0 && mode() < 2) {
                inputDelay = (inputDelay - 1) & 0xff;
                release();
                return;
            }
        }
        if ((input & (disableOnBlockBreak.get() ? 1 : 0)) == 1) {
            HitResult hit = c.client.crosshairTarget;
            if (hit != null && hit.type.ordinal() == 1) {
                active = 0;
                return;
            }
        }
        if (requireWeapon.get()
                && !HeldItem.holdingWeapon(runtime, player)) {
            release();
            return;
        }
        Vec3 origin = position(player).addStandingEyeHeight();
        SharedRotation rotation = runtime.sharedRotation;
        Rotation angles = rotation.referenceAvailable ? rotation.reference : RotationLifecycle.read(player);
        Limits limits = new Limits(range.high(),
                fov.high());
        Output output = new Output();
        if (modeTarget.index() == 0) {
            Limits retained = new Limits(limits.distance, limits.yaw);
            PlayerEntity held = runtime.aimTargetReference;
            if (held != null) {
                output.flag = active;
                if (consider(c, held, origin, angles, true, retained, output)) {
                    active = output.flag;
                    previousTarget = currentTarget;
                    currentTarget = output.point;
                    return;
                }
                release();
            }
        }
        List<PlayerEntity> players = c.world.players;
        PlayerEntity selected = null;
        int flagByte = 0;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity candidate = players.get(index);
            if (candidate == player) {
                continue;
            }
            output.flag = flagByte;
            if (consider(c, candidate, origin, angles, false, limits, output)) {
                selected = candidate;
            }
            flagByte = output.flag;
        }
        if (selected == null) {
            release();
            return;
        }
        previousTarget = runtime.aimTargetReference == selected ? currentTarget : output.point;
        currentTarget = output.point;
        active = flagByte;
        runtime.aimTargetReference = selected;
    }

    private static class Limits {
        double distance;
        float metric = Float.intBitsToFloat(0x7f7fffff);
        int hurtTime = Integer.MAX_VALUE;
        int tieHash;
        float yaw;

        Limits(double distance, float yaw) {
            this.distance = distance;
            this.yaw = yaw;
        }
    }

    private static class Output {
        Vec3 point;
        int flag;
    }

    private boolean consider(ClientGameContext c, PlayerEntity entity, Vec3 origin, Rotation angles,
                             boolean allowOutsideYaw, Limits limits, Output output) {
        if (entity.removed || entity.sleeping) {
            return false;
        }
        if (ignoreInvis.get() && entity.isInvisibleTo(c.player)) {
            return false;
        }
        if (PlayerTags.has(entity, 7)) {
            return false;
        }
        Vec3 position = position(entity);
        float predict = predictSetting.get();
        if (0.0f < predict) {
            RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) entity;
            position = position.interpolate(new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ), predict);
        }
        Bounds box = Geometry.expandBounds(AttackPrediction.bounds(entity.getShape()), 0.1f);
        Vec3 closest = Geometry.nearestPoint(box, origin);
        double separation = Geometry.distance(origin, closest);
        double threshold = range.low();
        int flag = !(threshold > separation) ? 1 : 0;
        if (separation > limits.distance) {
            return false;
        }
        Vec3 eye = position.addStandingEyeHeight();
        double pointX = eye.x;
        double pointZ = eye.z;
        float horizontal = horHitboxCorrect.get();
        if (horizontal > 0.0f) {
            float blend = horizontal;
            if (3.5 > separation) {
                blend = (float) ((double) blend * (separation / 3.5));
            }
            Vec3 blended = eye.interpolate(closest, blend);
            pointX = blended.x;
            pointZ = blended.z;
        }
        double pointY = Geometry.interpolateScalar(eye.y, closest.y,
                verHitboxCorrect.get());
        float coordinate = (float) ((origin.x + origin.y + origin.z + pointX + pointY + pointZ) * 0.1);
        float coordinateY = 10000.0f + coordinate;
        float coordinateZ = 20000.0f + coordinate;
        float noiseX = Geometry.gradientNoise(coordinate);
        float noiseY = Geometry.gradientNoise(coordinateY);
        Vec3 jitter = new Vec3((double) noiseX * 0.5, (double) noiseY * 0.5,
                (double) Geometry.gradientNoise(coordinateZ) * 0.5);
        float amount = aimRandomization.get();
        if (3.5 > separation) {
            amount = (float) ((double) amount * ((separation / 3.5) * 0.9 + 0.1));
        }
        Vec3 point = new Vec3(pointX, pointY, pointZ).add(jitter.scale(amount));
        Rotation difference = Geometry.directionBetween(origin, point).subtract(angles);
        float yaw = Floats.magnitude(Geometry.wrapYaw(difference.yaw));
        if (yaw > limits.yaw) {
            if (!allowOutsideYaw) {
                return false;
            }
            flag = (allowExceedFov.get() ? 1 : 0) & 0xff;
        }
        float metric = AttackPrediction.health(entity);
        if (metric > limits.metric) {
            return false;
        }
        int hurtTime = entity.damagedTimer;
        if (hurtTime > limits.hurtTime) {
            return false;
        }
        if (requireLineOfSight.get()) {
            Vec3d start = new Vec3d(origin.x, origin.y, origin.z);
            Vec3d end = new Vec3d(point.x, point.y, point.z);
            if (c.world.rayTrace(start, end, false, true, true) != null) {
                return false;
            }
        }
        if (modeTarget.index() == 0) {
            limits.yaw = yaw;
        } else {
            switch (sortBy.index()) {
                case 0:
                    limits.distance = separation;
                    break;
                case 1:
                    limits.metric = metric;
                    break;
                case 2: {
                    int hash = entity.networkId + (int) tieCounter;
                    hash = ((hash >>> 16) ^ hash) * 0x45d9f3b;
                    hash = ((hash >>> 16) ^ hash) * 0x45d9f3b;
                    hash = (hash >>> 16) ^ hash;
                    ++tieCounter;
                    if (hurtTime == limits.hurtTime && Integer.compareUnsigned(hash, limits.tieHash) < 0) {
                        return false;
                    }
                    limits.hurtTime = hurtTime;
                    limits.tieHash = hash;
                    break;
                }
                case 3:
                    limits.yaw = yaw;
                    break;
                default:
                    break;
            }
        }
        output.point = point;
        output.flag = flag;
        return true;
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%s", modeSetting.choice(mode()));
    }

    @Override
    protected void setup() {
        on(EventTypes.FRAME, e -> frame(e));
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.ROTATION_REQUEST, e -> flags(e), -100);
        on(EventTypes.UPDATE_POST, e -> acquire((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Aim");
        w.show(modeSetting);
        if (modeSetting.index() == 3) {
            w.show(silentMoveCorrect);
            w.show(silentIgnoreManualAim);
            w.show(horSpeed);
            w.show(verSpeed);
        } else if (modeSetting.index() == 2)
            w.show(lockOnVertical);
        else {
            w.show(horSpeed);
            w.show(verSpeed);
        }
        w.show(predictSetting);
        w.show(aimRandomization);
        w.heading("Multi-point");
        w.show(horHitboxCorrect);
        w.show(verHitboxCorrect);
        w.heading("Target");
        w.show(modeTarget);
        if (modeTarget.index() == 1) w.show(sortBy);
        else if (modeTarget.index() == 0)
            w.show(allowExceedFov);
        w.show(range);
        w.show(fov);
        w.heading("Conditions");
        if (modeSetting.index() != 3) w.show(requireSprint);
        w.show(requireMousePressed);
        w.show(requireMouseMoved);
        w.show(disableOnBlockBreak);
        w.show(requireWeapon);
        w.show(ignoreInvis);
        w.show(requireLineOfSight);
    }
}
