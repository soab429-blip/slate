package yt.ren.slate.feature.combat;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.attack.AttackTargetCache;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.DrawList;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Rotation;

import java.util.concurrent.atomic.AtomicInteger;

public class Backtrack extends Module {
    private final RangeSetting targetDist = register("target_dist", new RangeSetting("Target distance", "%.1f blocks", "%.1f - %.1f blocks", 0.1f, 0.0f, 0.0f, 12.0f, 2.0f, 5.0f, 0.0f, 0.0f));
    private final ScalarSetting delay = register("delay", new ScalarSetting("Maximum delay", "%.0f ms", 0.0f, 0.0f, 10.0f, 1000.0f, 100.0f, 0.0f));
    private final ScalarSetting maxHurtTime = register("max_hurt_time", new ScalarSetting("Maximum hurt time", "%.0f ms", 1.0f, 50.0f, 1.0f, 10.0f, 10.0f, 0.0f));
    private final ScalarSetting cooldownSetting = register("cooldown", new ScalarSetting("Cooldown", "%.1f s", 1.0f, 0.05f, 0.0f, 100.0f, 5.0f, 0.0f));
    private final BooleanSetting disableOnHitSetting = register("disable_on_hit", new BooleanSetting("Disable when hit", 0, 0));
    private final BooleanSetting renderRemotePos = register("render_remote_pos", new BooleanSetting("Enabled", 0, 0));
    private final ColorSetting indicatorColor = register("indicator_color", new ColorSetting("Color", 0.99f, 0.99f, 0.99f, 0.0f, 0.0f, 0.0f));
    private final ScalarSetting indicatorLineWidth = register("indicator_line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));
    private final BooleanSetting indicatorFilled = register("indicator_filled", new BooleanSetting("Filled", 1, 0));
    private final BooleanSetting indicatorRotate = register("indicator_rotate", new BooleanSetting("Apply head rotation", 1, 0));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));
    private final BooleanSetting requireWeapon = register("require_weapon", new BooleanSetting("Holding weapon", 0, 0));

    private final ClientRuntime runtime;

    private int lastDelayedId;
    private int lastReleasedId;
    private final AtomicInteger lock = new AtomicInteger();
    private long maximumDelay;
    private int targetId;
    private int playerId;
    private int active;
    private int disableOnHit;
    private Vec3 pendingPosition = Vec3.ZERO;
    private int absolutePosition;
    private float packetYaw;
    private int hasPacketYaw;
    private int absolutePacketId;
    private volatile int renderEnabled;
    private Vec3 previousRender = Vec3.ZERO;
    private Vec3 currentRender = Vec3.ZERO;
    private float previousYaw;
    private float currentYaw;
    private int cooldown;
    private Vec3 lastObserved = Vec3.ZERO;
    private float lastObservedYaw;
    private int interpolationTicks;

    public Backtrack(ClientRuntime runtime) {
        super(ModuleId.BACKTRACK, "Backtrack", "Uses lag to make players easier to hit.", Category.COMBAT);
        this.runtime = runtime;
    }



    private void acquire() {
        while (lock.getAndSet(1) != 0) {
        }
    }

    private void release() {
        lock.set(0);
    }

    private static float decodedYaw(byte value) {
        return (float) value * 360.0f * 0.00390625f;
    }

    private void applyPacket(PacketEvent event, int released) {
        if (released != 0 && absolutePacketId > event.id) {
            return;
        }
        if (event.packet instanceof EntityMoveS2CPacket) {
            EntityMoveS2CPacket relative = (EntityMoveS2CPacket) event.packet;
            if (relative.id != targetId) {
                return;
            }
            Vec3 delta = new Vec3((double) relative.dx * 0.03125, (double) relative.dy * 0.03125,
                    (double) relative.dz * 0.03125);
            if (released != 0) {
                pendingPosition = pendingPosition.subtract(delta);
                return;
            }
            pendingPosition = pendingPosition.add(delta);
            if (relative.hasAngles) {
                packetYaw = decodedYaw(relative.yaw);
                hasPacketYaw = 1;
            }
            return;
        }
        if (!(event.packet instanceof EntityTeleportS2CPacket)) {
            return;
        }
        EntityTeleportS2CPacket absolute = (EntityTeleportS2CPacket) event.packet;
        if (absolute.id != targetId) {
            return;
        }
        Vec3 point = new Vec3((double) absolute.x * 0.03125, (double) absolute.y * 0.03125,
                (double) absolute.z * 0.03125);
        if (released != 0) {
            pendingPosition = pendingPosition.subtract(point);
            absolutePosition = 0;
            return;
        }
        pendingPosition = point;
        absolutePosition = 1;
        packetYaw = decodedYaw(absolute.yaw);
        hasPacketYaw = 1;
        absolutePacketId = event.id;
    }

    void filterPacket(PacketEvent event) {
        acquire();
        try {
            if (active != 1) {
                return;
            }
            Object packet = event.packet;
            if (packet instanceof EntityEventS2CPacket) {
                if (((EntityEventS2CPacket) packet).id == targetId) {
                    event.action = 3;
                    return;
                }
            } else if (packet instanceof PlayerHealthS2CPacket || packet instanceof SoundEventS2CPacket) {
                event.action = 3;
                return;
            }
            if (packet instanceof EntityVelocityS2CPacket) {
                if (disableOnHit != 0 && ((EntityVelocityS2CPacket) packet).id == playerId) {
                    active = 0;
                    return;
                }
            } else if (packet instanceof PlayerMoveS2CPacket) {
                active = 0;
                return;
            }
            if (event.elapsed <= maximumDelay) {
                event.action = 1;
                if (event.id > lastDelayedId) {
                    lastDelayedId = event.id;
                    applyPacket(event, 0);
                }
            } else if (event.action == 0 && event.id > lastReleasedId) {
                applyPacket(event, 1);
                lastReleasedId = event.id;
            }
        } finally {
            release();
        }
    }

    private void smoothPosition(Vec3 point, float yaw) {
        boolean moved = Double.doubleToRawLongBits(lastObserved.x) != Double.doubleToRawLongBits(point.x)
                || Double.doubleToRawLongBits(lastObserved.y) != Double.doubleToRawLongBits(point.y)
                || Double.doubleToRawLongBits(lastObserved.z) != Double.doubleToRawLongBits(point.z);
        if (moved || yaw != lastObservedYaw) {
            interpolationTicks = 3;
        }
        int count = interpolationTicks;
        lastObserved = point;
        previousRender = currentRender;
        lastObservedYaw = yaw;
        previousYaw = currentYaw;
        if (count > 0) {
            double fraction = 1.0 / (double) count;
            Vec3 movement = point.subtract(currentRender).scale(fraction);
            currentRender = currentRender.add(movement);
            float difference = Geometry.wrapYaw(lastObservedYaw - currentYaw);
            yaw = (float) ((double) difference * fraction) + currentYaw;
            --interpolationTicks;
        } else {
            currentRender = point;
        }
        currentYaw = yaw;
    }

    private static boolean attackPressed(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0;
    }

    private boolean eligible(ClientGameContext c) {
        if (c.focused != 1) {
            return false;
        }
        if (requireMousePressed.get() && !attackPressed(c)) {
            return false;
        }
        if (requireWeapon.get()
                && !HeldItem.holdingWeapon(runtime, c.player)) {
            return false;
        }
        int attack = c.player.lastAttackTime;
        int ticks = c.player.ticks;
        if (ticks - attack > 20) {
            return false;
        }
        AttackTargetCache cache = c.targetCache;
        if (cache.target == null) {
            return false;
        }
        int hurt = cache.target.damagedTimer;
        if ((float) hurt > maxHurtTime.get()) {
            return false;
        }
        float minimum = targetDist.low();
        double squared = cache.squaredDistance;
        if ((double) (minimum * minimum) > squared) {
            return false;
        }
        float maximum = targetDist.high();
        return !(squared > (double) (maximum * maximum));
    }

    void update(ClientGameContext c) {
        if (cooldown > 0) {
            --cooldown;
            if (cooldown != 0) {
                return;
            }
        }
        if (!eligible(c)) {
            acquire();
            if (active == 1) {
                cooldown = Sse.cvttss2si(cooldownSetting.get());
                active = 0;
            }
            release();
            renderEnabled = 0;
            return;
        }
        Vec3 local = new Vec3(c.player.x, c.player.y, c.player.z);
        PlayerEntity target = c.targetCache.target;
        RemoteClientPlayerEntity remote = (RemoteClientPlayerEntity) target;
        Vec3 server = new Vec3(remote.lerpX, remote.lerpY, remote.lerpZ);
        Rotation rotation = RotationLifecycle.read(target);
        int newTargetId = target.networkId;
        int newPlayerId = c.player.networkId;
        acquire();
        try {
            int previousId = targetId;
            maximumDelay = Sse.cvttss2si64(delay.get());
            targetId = newTargetId;
            playerId = newPlayerId;
            disableOnHit = (disableOnHitSetting.get() ? 1 : 0);
            if (active != 1) {
                Vec3 position = new Vec3(target.x, target.y, target.z);
                double before = Geometry.distance(local, position);
                if (!(Geometry.distance(local, server) - before > 0.01)) {
                    renderEnabled = 0;
                    return;
                }
                active = 1;
                pendingPosition = Vec3.ZERO;
                absolutePosition = 0;
                hasPacketYaw = 0;
                Vec3 initial = new Vec3(target.x, target.y, target.z);
                previousRender = initial;
                currentRender = initial;
                lastObserved = initial;
                previousYaw = rotation.yaw;
                currentYaw = rotation.yaw;
                lastObservedYaw = rotation.yaw;
                renderEnabled = (renderRemotePos.get() ? 1 : 0);
                return;
            }
            Vec3 pending = absolutePosition == 1 ? pendingPosition : pendingPosition.add(server);
            double gain = Geometry.distance(local, pending) - Geometry.distance(local, server);
            if (!(-0.05 > gain) && previousId == newTargetId) {
                smoothPosition(pending, hasPacketYaw != 0 ? packetYaw : rotation.yaw);
                return;
            }
            pendingPosition = Vec3.ZERO;
            absolutePosition = 0;
            hasPacketYaw = 0;
            int value = Sse.cvttss2si(cooldownSetting.get());
            renderEnabled = 0;
            active = 0;
            cooldown = value;
        } finally {
            release();
        }
    }

    void render(WorldRenderEvent event) {
        if (renderEnabled != 1) {
            return;
        }
        double fraction = event.partialTick;
        Vec3 point = previousRender.interpolate(currentRender, fraction);
        Vec3 relative = point.subtract(event.camera);
        DrawList draw = event.draw;
        draw.lineWidth(indicatorLineWidth.get());
        draw.push();
        draw.translate((float) relative.x, (float) (relative.y + 1.0), (float) relative.z);
        draw.scale(0.3f, 1.0f, 0.3f);
        if (indicatorRotate.get()) {
            float yaw = previousYaw + (currentYaw - previousYaw) * event.partialTick;
            draw.rotate(180.0f - yaw, 0.0f, 1.0f, 0.0f);
        }
        if (indicatorFilled.get()) {
            float[] color = indicatorColor.liveChannels();
            draw.color(color[0], color[1], color[2], 0.4f);
            draw.fillCube();
        }
        float[] color = indicatorColor.liveChannels();
        draw.color(color[0], color[1], color[2]);
        draw.outlineCube();
        draw.pop();
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%.0fms", (double) delay.get());
    }

    @Override
    protected void setup() {
        on(EventTypes.PACKET_RECEIVE, e -> filterPacket(e));
        on(EventTypes.WORLD_RENDER, e -> render(e));
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(targetDist);
        w.show(delay);
        w.show(maxHurtTime);
        w.show(cooldownSetting);
        w.show(disableOnHitSetting);
        w.heading("Real position indicator");
        w.show(renderRemotePos);
        if (renderRemotePos.get()) {
            w.show(indicatorColor);
            w.show(indicatorLineWidth);
            w.show(indicatorFilled);
            w.show(indicatorRotate);
        }
        w.heading("Conditions");
        w.show(requireMousePressed);
        w.show(requireWeapon);
    }
}
