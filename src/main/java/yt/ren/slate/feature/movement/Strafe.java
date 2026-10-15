package yt.ren.slate.feature.movement;

import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.MoveEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Vec3;

public class Strafe extends Module {
    private final ScalarSetting strengthJump = register("strength_jump", new ScalarSetting("Jump", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting strengthGround = register("strength_ground", new ScalarSetting("Ground", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting strengthAir = register("strength_air", new ScalarSetting("Air", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final ScalarSetting maxHurtTime = register("max_hurt_time", new ScalarSetting("Maximum hurt time", "%.0f ms", 1.0f, 50.0f, 0.0f, 10.0f, 10.0f, 0.0f));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;
    private int strafeWasSprinting;

    public Strafe(ClientRuntime runtime) {
        super(ModuleId.STRAFE, "Strafe", "Allows faster strafing.", Category.MOVEMENT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.MOVE_PRE, this::captureStrafeSprint, 1000);
        on(EventTypes.MOVE_POST, this::updateStrafe, 100);
    }

    void captureStrafeSprint(MoveEvent event) {
        strafeWasSprinting = event.game().player.isSprinting() ? 1 : 0;
    }

    void updateStrafe(MoveEvent event) {
        if (Motion.stationary(event)) {
            return;
        }
        ClientGameContext c = event.game();
        if (requireWeapon.get() && !HeldItem.holdingWeapon(runtime, c.player)) {
            return;
        }
        int hurt = c.player.damagedTimer;
        if ((float) hurt > maxHurtTime.get()) {
            return;
        }
        ScalarSetting strengthId = strengthAir;
        int extra = 0;
        if (c.player.onGround) {
            if (event.jumpPressed == 1) {
                extra = strafeWasSprinting ^ 1;
                strengthId = strengthJump;
            } else {
                strengthId = strengthGround;
            }
        }
        float strength = strengthId.get();
        if (strength == 0.0f) {
            return;
        }
        Vec3 v = Motion.velocity(c.player);
        double speed = CrtMath.hypot(v.x, v.z);
        if (extra != 0) {
            speed = speed + 0.2;
        }
        double yaw = RotationLifecycle.read(c.player).yaw;
        double direction = event.strafe < 0.0f ? -1.0 : 0.0;
        if (event.strafe > 0.0f) {
            direction = 1.0;
        }
        if (event.forward > 0.0f) {
            direction = direction * -45.0;
        } else if (event.forward < 0.0f) {
            yaw = yaw + 180.0;
            direction = direction * 45.0;
        } else {
            direction = direction * -90.0;
        }
        float radians = (float) ((direction + yaw) * Double.longBitsToDouble(0x400921fb54442d18L) / 180.0);
        float negativeSine = Float.intBitsToFloat(Float.floatToRawIntBits(ScalarTrig.sine(radians)) ^ 0x80000000);
        double vx = Geometry.interpolateScalar(v.x, (double) negativeSine * speed, strength);
        double vz = Geometry.interpolateScalar(v.z, (double) ScalarTrig.cosine(radians) * speed, strength);
        Motion.writeVelocity(c.player, new Vec3(vx, v.y, vz));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Strength");
        w.show(strengthJump);
        w.show(strengthGround);
        w.show(strengthAir);
        w.heading("Conditions");
        w.show(maxHurtTime);
        w.show(requireWeapon);
    }
}
