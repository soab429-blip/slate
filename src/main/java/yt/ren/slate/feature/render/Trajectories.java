package yt.ren.slate.feature.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.aim.rotation.SharedRotation;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    private final ScalarSetting lineWidth = register("line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));
    private final ColorSetting colorHit = register("color_hit", new ColorSetting("Hit color", 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorMiss = register("color_miss", new ColorSetting("Miss color", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final BooleanSetting renderThrowables = register("render_throwables", new BooleanSetting("Snowballs/eggs", 1, 0));
    private final BooleanSetting renderPearls = register("render_pearls", new BooleanSetting("Enderpearls", 1, 0));
    private final BooleanSetting renderBow = register("render_bow", new BooleanSetting("Bow", 1, 0));
    private final BooleanSetting renderUnchargedBow = register("render_uncharged_bow", new BooleanSetting("Bow (uncharged)", 0, 0));

    private static final double RADIANS = Double.longBitsToDouble(0x3f91df46a2529d39L);
    private static final double DRAG = Double.longBitsToDouble(0x3fefae147ae147aeL);

    private final SharedRotation rotation;

    private Vec3 targetMotion = Vec3.ZERO;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Bounds> bounds = new ArrayList<>();
    private int pointCount;
    private int hitColor;
    private final FloatBuffer points = BufferUtils.createFloatBuffer(300);
    private int showBounds;
    private Bounds hitBounds = new Bounds(Vec3.ZERO, Vec3.ZERO);
    private float gravity;
    private float previousSpeed;
    private float currentSpeed;

    public Trajectories(ClientRuntime runtime) {
        super(ModuleId.TRAJECTORIES, "Trajectories", "Predicts the trajectory of the item in your hand.", Category.VISUAL);
        this.rotation = runtime.sharedRotation;
    }


    private static float interpolateFloat(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static Item heldItem(PlayerEntity player) {
        PlayerInventory inventory = player.inventory;
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }

    private static Bounds entityBounds(Box box) {
        return new Bounds(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
    }

    private static Vec3 hitBlock(HitResult hit) {
        BlockPos pos = hit.pos;
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    private static float bowSpeed(int duration) {
        float time = (float) duration / 20.0f;
        float doubled = time + time;
        float squared = time * time;
        float power = (squared + doubled) / 3.0f;
        if ((double) power < 0.1) {
            return 0.0f;
        }
        float clamped = 1.0f < power ? 1.0f : power;
        double half = (double) clamped * 1.5;
        return (float) (half + half);
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.state != 0 || event.module != descriptor) {
            return;
        }
        entities.clear();
        bounds.clear();
    }

    private Vec3[] launch(ClientGameContext c, Vec3 point, Rotation angles, float partial) {
        float yaw = (float) ((double) angles.yaw * RADIANS);
        float cosYaw = ScalarTrig.cosine(yaw);
        float pitch = (float) ((double) angles.pitch * RADIANS);
        float cosPitch = ScalarTrig.cosine(pitch);
        float sinYaw = ScalarTrig.sine(yaw);
        float sinPitch = ScalarTrig.sine(pitch);
        Vec3 direction = new Vec3(-sinYaw * cosPitch, -sinPitch, cosPitch * cosYaw);
        float speed = interpolateFloat(previousSpeed, currentSpeed, partial);
        Vec3 velocity = direction.scale(speed);
        point = point.addStandingEyeHeight();
        double y = point.y;
        if (c.player.isSneaking()) {
            y = y + -0.08;
        }
        double x = point.x - (double) cosYaw * 0.16;
        y = y + 0.1;
        double z = point.z - (double) sinYaw * 0.16;
        return new Vec3[]{new Vec3(x, y, z), velocity};
    }

    void update(ClientGameContext c) {
        if (c.focused != 1 || rotation.referenceAvailable) {
            pointCount = 0;
            return;
        }
        if (c.client.options.perspective != 0) {
            pointCount = 0;
            return;
        }
        Item item = heldItem(c.player);
        if (item == null) {
            pointCount = 0;
            return;
        }
        int id = Item.getId(item);
        int blockColor = 0;
        int entityColor = 1;
        if (id == 261) {
            if (!renderBow.get()) {
                pointCount = 0;
                return;
            }
            gravity = Float.intBitsToFloat(0x3d4ccccd);
            if (!c.player.hasItemInUse()) {
                if (!renderUnchargedBow.get()) {
                    pointCount = 0;
                    return;
                }
                previousSpeed = 3.0f;
                currentSpeed = 3.0f;
            } else {
                int remaining = c.player.itemUseTimer;
                currentSpeed = bowSpeed(72000 - remaining);
                if (currentSpeed == 0.0f) {
                    pointCount = 0;
                    return;
                }
                previousSpeed = bowSpeed(71999 - remaining);
            }
        } else if (id == 368 || id == 332 || id == 344) {
            boolean pearl = id == 368;
            if (!(pearl ? renderPearls : renderThrowables).get()) {
                pointCount = 0;
                return;
            }
            previousSpeed = 1.5f;
            currentSpeed = 1.5f;
            gravity = Float.intBitsToFloat(0x3cf5c28f);
            if (pearl) {
                blockColor = 1;
                entityColor = 0;
            }
        } else {
            pointCount = 0;
            return;
        }
        Rotation angles = RotationLifecycle.read(c.player);
        Vec3 point = new Vec3(c.player.x, c.player.y, c.player.z);
        Vec3[] launched = launch(c, point, angles, 0.0f);
        Vec3 origin = launched[0];
        Vec3 motion = launched[1];
        entities.clear();
        bounds.clear();
        List<Entity> list = c.world.entities;
        Bounds broadphase = Geometry.orderedBounds(new Vec3(origin.x, -100.0, origin.z),
                new Vec3(motion.x * 1000.0 + origin.x, origin.y + 200.0, 1000.0 * motion.z + origin.z));
        if (list.size() > 0) {
            int index = 0;
            do {
                Entity entity = list.get(index);
                if (entity != c.player && entity.hasCollision()) {
                    Bounds box = Geometry.expandBounds(entityBounds(entity.shape), 0.3);
                    if (Geometry.overlaps(box, broadphase)) {
                        entities.add(entity);
                        bounds.add(box);
                    }
                }
                ++index;
            } while (index < list.size());
        }
        for (int step = 0; step < 100; ++step) {
            Vec3 previous = origin;
            origin = origin.add(motion);
            Vec3d from = new Vec3d(previous.x, previous.y, previous.z);
            Vec3d to = new Vec3d(origin.x, origin.y, origin.z);
            HitResult hit = c.world.rayTrace(from, to, false, true, false);
            if (hit != null) {
                pointCount = step + 1;
                showBounds = 1;
                hitColor = blockColor;
                Vec3d hitVector = hit.facePos;
                origin = new Vec3(hitVector.x, hitVector.y, hitVector.z);
                motion = origin.subtract(previous);
                Vec3 block = hitBlock(hit);
                hitBounds = new Bounds(block, block.add(new Vec3(1.0, 1.0, 1.0)));
                targetMotion = Vec3.ZERO;
            }
            Vec3 inverse = Geometry.reciprocalComponents(motion);
            for (int index = 0; index < bounds.size(); ++index) {
                Bounds candidate = bounds.get(index);
                if (Geometry.segmentIntersectsBounds(candidate, previous, inverse)) {
                    pointCount = step + 1;
                    showBounds = 1;
                    hitColor = entityColor;
                    hitBounds = candidate;
                    Entity entity = entities.get(index);
                    Vec3 current = new Vec3(entity.x, entity.y, entity.z);
                    Vec3 prior = new Vec3(entity.lastX, entity.lastY, entity.lastZ);
                    targetMotion = current.subtract(prior);
                    return;
                }
            }
            if (hit != null) {
                return;
            }
            motion = motion.scale(DRAG);
            motion = new Vec3(motion.x, motion.y - (double) gravity, motion.z);
        }
        pointCount = 100;
        showBounds = 0;
        hitColor = 0;
    }

    void render(WorldRenderEvent event) {
        if (pointCount == 0) {
            return;
        }
        ClientGameContext c = event.game();
        Rotation angles = RotationLifecycle.read(c.player);
        Vec3 current = new Vec3(c.player.x, c.player.y, c.player.z);
        Vec3 previous = new Vec3(c.player.lastX, c.player.lastY, c.player.lastZ);
        Vec3 point = previous.interpolate(current, event.partialTick);
        Vec3 camera = event.camera;
        Vec3[] launched = launch(c, point, angles, event.partialTick);
        Vec3 origin = launched[0];
        Vec3 motion = launched[1];
        if (pointCount != 0) {
            int index = 0;
            do {
                origin = origin.add(motion);
                points.put(index * 3, (float) (origin.x - camera.x));
                points.put(index * 3 + 1, (float) (origin.y - camera.y));
                points.put(index * 3 + 2, (float) (origin.z - camera.z));
                motion = motion.scale(DRAG);
                motion = new Vec3(motion.x, motion.y - (double) gravity, motion.z);
                ++index;
            } while (index < pointCount);
        }
        GL11.glEnable(0xbe2);
        GL11.glLineWidth(lineWidth.get());
        float[] color = (hitColor != 0 ? colorHit : colorMiss).liveChannels();
        GL11.glColor3f(color[0], color[1], color[2]);
        GL11.glEnableClientState(0x8074);
        points.clear();
        GL11.glVertexPointer(3, 0, points);
        GL11.glDrawArrays(3, 0, pointCount);
        if (showBounds == 1) {
            Vec3 center = hitBounds.minimum.interpolate(hitBounds.maximum, 0.5);
            Vec3 halfSize = hitBounds.maximum.subtract(center);
            Vec3 interpolatedMotion = targetMotion.scale(1.0 - (double) event.partialTick);
            Vec3 relative = center.subtract(event.camera);
            Vec3 position = relative.subtract(interpolatedMotion);
            GL11.glPushMatrix();
            GL11.glTranslatef((float) position.x, (float) position.y, (float) position.z);
            GL11.glScalef((float) halfSize.x, (float) halfSize.y, (float) halfSize.z);
            Primitives.beginCubeVertices();
            Primitives.drawCubeOutline();
            GL11.glPopMatrix();
        }
        Primitives.endPrimitiveVertices();
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.WORLD_RENDER, e -> render(e), -1000);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(lineWidth);
        w.show(colorHit);
        w.show(colorMiss);
        w.heading("Render when holding");
        w.show(renderThrowables);
        w.show(renderPearls);
        w.show(renderBow);
        if (renderBow.get())
            w.show(renderUnchargedBow);
    }
}
