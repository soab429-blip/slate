package yt.ren.slate.feature.movement;

import yt.ren.slate.aim.AimRandom;
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
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Rotation;

public class FastAccel extends Module {
    private final ScalarSetting acceleration = register("acceleration", new ScalarSetting("Boost", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.0f, 0.0f));
    private final BooleanSetting requireNotDamaged = register("require_not_damaged", new BooleanSetting("Not damaged", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;
    private final AimRandom random;

    public FastAccel(ClientRuntime runtime) {
        super(ModuleId.FAST_ACCEL, "Fast Accel", "Speeds up acceleration.", Category.MOVEMENT);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    @Override
    protected void setup() {
        on(EventTypes.MOVE_POST, this::updateFastAccel);
    }

    void updateFastAccel(MoveEvent event) {
        if (Motion.stationary(event)) {
            return;
        }
        ClientGameContext c = event.game();
        if (requireWeapon.get() && !HeldItem.holdingWeapon(runtime, c.player)) {
            return;
        }
        if (requireNotDamaged.get() && c.player.damagedTimer > 0) {
            return;
        }
        Vec3 v = Motion.velocity(c.player);
        double speed = CrtMath.hypot(v.x, v.z);
        if (speed == 0.0 || !c.player.onGround) {
            return;
        }
        float length = Geometry.angleLength(new Rotation(event.strafe, event.forward));
        float divisor = Motion.maxss(1.0f, length);
        float friction = event.friction / divisor;
        float strafe = event.strafe * friction;
        float forward = friction * event.forward;
        double inputLength = Geometry.angleLength(new Rotation(strafe, forward));
        double estimate = 0.0 + inputLength;
        for (int i = 0; i < 19; ++i) {
            estimate = estimate * 0.546 + inputLength;
        }
        if (event.jumpPressed == 1 && c.player.isSprinting()) {
            estimate = estimate + 0.2;
        }
        if (speed > estimate) {
            return;
        }
        double next = Geometry.interpolateScalar(speed, estimate, acceleration.get());
        Motion.writeVelocity(c.player, new Vec3(v.x / speed * next, v.y, v.z / speed * next));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(acceleration);
        w.heading("Conditions");
        w.show(requireNotDamaged);
        w.show(requireWeapon);
    }
}
