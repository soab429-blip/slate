package yt.ren.slate.game.attack;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

import java.util.List;

public class AttackPrediction {
    private static final float DEFAULT_FACTOR = Float.intBitsToFloat(0x3cd4fdf4);

    public static int hitKind(HitResult hit) {
        return hit.type.ordinal();
    }

    static Vec3 origin(ClientGameContext c) {
        return new Vec3(c.player.x, c.player.y, c.player.z).addStandingEyeHeight();
    }

    public static float health(LivingEntity player) {
        float value = player.getHealth();
        return (Float.floatToRawIntBits(value) & 0x7fffffff) > 0x7f800000 ? 20.0f : value;
    }

    public static Bounds bounds(Box box) {
        return new Bounds(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
    }

    static double targetDistance(Entity target, Vec3 from) {
        Bounds expanded = Geometry.expandBounds(bounds(target.getShape()), 0.1);
        return Geometry.distanceSquared(from, Geometry.nearestPoint(expanded, from));
    }

    public static void predictAttackSlowdown(ClientGameContext c, AttackTargetCache cache) {
        if ((cache.flags & 1) != 0) {
            return;
        }
        boolean closer = true;
        HitResult hit = c.client.crosshairTarget;
        if (hit != null && hitKind(hit) == 2) {
            Vec3 point = new Vec3(hit.facePos.x, hit.facePos.y, hit.facePos.z);
            Vec3 from = origin(c);
            float factor = c.player.onGround ? c.player.speed : DEFAULT_FACTOR;
            GameOptions options = c.client.options;
            boolean ignored = options.forwardKey.pressed | options.backKey.pressed
                    | options.leftKey.pressed | options.rightKey.pressed;
            Rotation rotation = new Rotation(c.player.yaw, c.player.pitch);
            float forward = c.player.forwardSpeed;
            float strafe = c.player.sidewaysSpeed;
            Vec3 acceleration = MathUtil.movementAcceleration(rotation, forward, strafe, factor);
            Vec3 velocity = new Vec3(c.player.velocityX, c.player.velocityY, c.player.velocityZ);
            Vec3 slowed = new Vec3(c.player.velocityX * 0.6, c.player.velocityY, 0.6 * c.player.velocityZ);
            Vec3 fullNext = from.add(velocity.add(acceleration));
            Vec3 slowNext = from.add(slowed.add(acceleration));
            double slowDistance = Geometry.distanceSquared(slowNext, point);
            double fullDistance = Geometry.distanceSquared(fullNext, point);
            closer = fullDistance > slowDistance;
        }
        cache.slowdownMovesCloser = closer ? 1 : 0;
        cache.flags |= 1;
    }

    public static boolean refreshAttackTarget(ClientGameContext c, AttackTargetCache cache, PlayerEntity aimTarget) {
        cache.flags = 0;
        if (c.world == null) {
            clearAttackTarget(cache);
            return true;
        }
        Vec3 from = origin(c);
        HitResult hit = c.client.crosshairTarget;
        PlayerEntity selected = null;
        if (hit != null && hitKind(hit) == 2) {
            Entity entity = hit.entity;
            if (entity instanceof RemoteClientPlayerEntity) {
                PlayerEntity player = (PlayerEntity) entity;
                if (!PlayerTags.has(player, 7) && !(0.0f >= health(player))) {
                    selected = player;
                }
            }
        }
        if (selected == null) {
            selected = aimTarget;
        }
        if (selected != null) {
            cache.target = selected;
            cache.squaredDistance = targetDistance(selected, from);
            return true;
        }
        Rotation rotation = new Rotation(c.player.yaw, c.player.pitch);
        List<PlayerEntity> list = c.world.players;
        double best = 225.0;
        int count = list.size();
        for (int index = 0; index < count; count = list.size()) {
            PlayerEntity candidate = list.get(index);
            if (c.player != candidate && !PlayerTags.has(candidate, 7) && !(0.0f >= health(candidate))
                    && !candidate.sleeping) {
                double distance = targetDistance(candidate, from);
                if (!(distance > best)) {
                    Vec3 position = new Vec3(candidate.x, candidate.y, candidate.z);
                    Rotation desired = Geometry.directionBetween(from, position);
                    float yawDelta = desired.yaw - rotation.yaw;
                    float pitchDelta = desired.pitch - rotation.pitch;
                    float yaw = Geometry.wrapYaw(yawDelta);
                    float score = yaw * yaw + pitchDelta + pitchDelta;
                    if (!(score > 4900.0f)) {
                        best = distance;
                        selected = candidate;
                    }
                }
            }
            index++;
        }
        if (selected != null) {
            cache.squaredDistance = best;
        }
        cache.target = selected;
        return true;
    }

    public static void clearAttackTarget(AttackTargetCache cache) {
        cache.target = null;
    }
}
