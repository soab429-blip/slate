package yt.ren.slate.game;

import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.event.Events.MouseOverEvent;
import yt.ren.slate.feature.combat.Piercing;
import yt.ren.slate.feature.world.GhostHand;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

import java.util.List;

public class MouseOver {
    private final Piercing piercing;
    private final GhostHand ghostHand;

    public MouseOver(Piercing piercing, GhostHand ghostHand) {
        this.piercing = piercing;
        this.ghostHand = ghostHand;
    }

    public static PlayerEntity asPlayer(Entity entity) {
        return entity instanceof RemoteClientPlayerEntity ? (PlayerEntity) entity : null;
    }

    private static Vec3 vec(Vec3d value) {
        return new Vec3(value.x, value.y, value.z);
    }

    private static Bounds bounds(Box box) {
        return new Bounds(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
    }

    private static Box box(Bounds value) {
        return new Box(value.minimum.x, value.minimum.y, value.minimum.z, value.maximum.x, value.maximum.y,
                value.maximum.z);
    }

    public void updateMouseOverTarget(MouseOverEvent event) {
        ClientGameContext c = event.game();
        double reach = event.reach;
        double traceDistance = 1.5 + reach;
        boolean ignoreBlocks = piercing.ignoresBlocks(c);
        HitResult blockHit = c.player.rayTrace(traceDistance, 1.0f);
        Vec3d startVector = c.player.getEyePosition(1.0f);
        Vec3 start = vec(startVector);
        if (!ignoreBlocks && blockHit != null) {
            traceDistance = Geometry.distance(start, vec(blockHit.facePos));
        }
        Vec3 direction = MathUtil.lookDirection(new Rotation(c.player.yaw, c.player.pitch));
        Vec3 motion = direction.scale(traceDistance);
        Vec3 endPoint = start.add(motion);
        Vec3d end = new Vec3d(endPoint.x, endPoint.y, endPoint.z);
        List<Entity> list = c.world.entities;
        int count = list.size();
        int keepBlock = 0;
        if (blockHit != null) {
            keepBlock = AttackPrediction.hitKind(blockHit) == 1 ? 1 : 0;
        }
        Entity selected = null;
        Vec3d selectedPoint = null;
        int insideSeen = 0;
        double best = reach;
        for (int index = 0; index < count; index++) {
            Entity candidate = list.get(index);
            if (c.player == candidate || !candidate.hasCollision()) {
                continue;
            }
            Bounds expanded = Geometry.expandBounds(bounds(candidate.getShape()), event.expand);
            HitResult intersection = box(expanded).clip(startVector, end);
            if (!Geometry.containsStrict(expanded, start)) {
                if (intersection == null) {
                    continue;
                }
                Vec3d point = intersection.facePos;
                double nextDistance = Geometry.distance(start, vec(point));
                int rank = piercing.rank(c, selected, candidate, insideSeen != 0);
                boolean take;
                if (rank == 2) {
                    take = best == 0.0 || !(best <= nextDistance);
                } else {
                    take = rank == 0 && !(reach <= nextDistance);
                }
                if (take) {
                    selected = candidate;
                    selectedPoint = point;
                    best = nextDistance;
                }
            } else if (best < 0.0) {
                insideSeen = 1;
            } else {
                boolean replace = true;
                if (selected != null) {
                    insideSeen = 1;
                    replace = piercing.rank(c, selected, candidate, true) != 1;
                }
                if (replace) {
                    selectedPoint = startVector;
                    if (intersection != null) {
                        selectedPoint = intersection.facePos;
                    }
                    insideSeen = 1;
                    selected = candidate;
                    best = 0.0;
                }
            }
            int allow = ghostHand.allowsThrough(c, selected) ? 1 : 0;
            keepBlock = (keepBlock != 0 ? 1 : 0) & allow;
        }
        if ((keepBlock & 1) != 0) {
            c.client.crosshairTarget = blockHit;
            return;
        }
        if (selected == null) {
            return;
        }
        int permitted = (ignoreBlocks ? 1 : 0) | (blockHit == null ? 1 : 0) | (best <= traceDistance ? 1 : 0);
        if (permitted != 1) {
            return;
        }

        Bounds expanded = Geometry.expandBounds(bounds(selected.getShape()),
                Double.longBitsToDouble(0x3fb99999a0000000L));
        Vec3 position = vec(selectedPoint);
        Vec3 nearest = Geometry.nearestPoint(expanded, position);
        c.client.crosshairTarget = new HitResult(selected, new Vec3d(nearest.x, nearest.y, nearest.z));
    }

    public static void requestCustomMouseOver(MouseOverEvent event) {
        event.request = 1;
    }
}
