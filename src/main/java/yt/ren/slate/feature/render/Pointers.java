package yt.ren.slate.feature.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.game.NameColors;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.render.ScreenProjection;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.QSort;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class Pointers extends Module {
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f blocks", 0.0f, 0.0f, 10.0f, 1000.0f, 100.0f, 0.0f));
    private final ScalarSetting ignoreFov = register("ignore_fov", new ScalarSetting("Ignore within FOV", "%0.0f\u00b0", 1.0f, 2.0f, 0.0f, 180.0f, 0.0f, 0.0f));
    private final BooleanSetting hideFriendlies = register("hide_friendlies", new BooleanSetting("Hide friendlies", 0, 0));
    private final ChoiceSetting style = register("style", new ChoiceSetting("Style", 2, 0, new String[]{"2D", "3D"}, 0, 0, 0));
    private final ChoiceSetting colorMode = register("color_mode", new ChoiceSetting("Colors", 3, 0, new String[]{"Distance", "Name tag", "Manual"}, 0, 0, 0));
    private final RangeSetting nearFarRange = register("near_far_range", new RangeSetting("Transition between", "%0.0f blocks", "%0.0f - %0.0f blocks", 0.0f, 0.0f, 0.0f, 100.0f, 5.0f, 30.0f, 0.0f, 0.0f));
    private final ColorSetting colorNear = register("color_near", new ColorSetting("Near color", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorFar = register("color_far", new ColorSetting("Far color", 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorEnemies = register("color_enemies", new ColorSetting("Enemy color", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorFriendlies = register("color_friendlies", new ColorSetting("Friendly color", 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ScalarSetting scaleSetting = register("scale", new ScalarSetting("Scale", "%.0f%%", 0.0f, 100.0f, 0.75f, 2.0f, 1.0f, 0.0f));
    private final ScalarSetting radiusSetting = register("radius", new ScalarSetting("Radius", "%.0f", 1.0f, 0.0f, 5.0f, 20.0f, 10.0f, 0.0f));
    private final BooleanSetting scaleRadius = register("scale_radius", new BooleanSetting("Distance-based radius", 0, 0));


    private static class Entry {
        double currentX;
        double currentZ;
        double previousX;
        double previousZ;
        float[] color = new float[3];
        float previousDistance;
        float currentDistance;
    }

    private int count;
    private final Entry[] entries = new Entry[100];

    public Pointers(ClientRuntime runtime) {
        super(ModuleId.POINTERS, "Pointers", "Points to other players.", Category.VISUAL);
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
    }




    private static float interpolateFloat(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static float clampFraction(float value) {
        return value < 0.0f ? 0.0f : Floats.min(1.0f, value);
    }

    private static float horizontalAngle(double x, double z, double cameraX, double cameraZ, float viewYaw) {
        double dx = x - cameraX;
        double dz = z - cameraZ;
        double degrees = CrtMath.atan2(dz, dx) * 180.0 / 3.141592653589793 + -90.0;
        return Geometry.wrapYaw((float) degrees - viewYaw);
    }

    private static float currentDistance(double squared) {
        float value = (float) squared;
        if (!(value >= 0.0f)) {
            return (0.0f) / 0.0f;
        }
        return (float) Math.sqrt(value);
    }

    private static int compareDistance(Entry first, Entry second) {
        return Sse.cvttss2si(second.currentDistance - first.currentDistance);
    }

    void update(ClientGameContext c) {
        if (c.world == null) {
            return;
        }
        count = 0;
        Vec3 local = new Vec3(c.player.x, c.player.y, c.player.z);
        Vec3 previousLocal = new Vec3(c.player.lastX, c.player.lastY, c.player.lastZ);
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        float yaw = manager.cameraYaw;
        List<PlayerEntity> list = c.world.players;
        int hide = (hideFriendlies.get() ? 1 : 0);
        if (list.size() > 0) {
            int excluded = hide * 5 + 2;
            int index = 0;
            do {
                PlayerEntity player = list.get(index);
                if (player != c.player && !player.removed) {
                    int tags = PlayerTags.tags(player);
                    if ((tags & excluded) == 0) {
                        Vec3 point = new Vec3(player.x, player.y, player.z);
                        double squaredDistance = Geometry.distanceSquared(point, local);
                        float range = rangeSetting.get();
                        if (!(squaredDistance > (double) (range * range))) {
                            float angle = Floats.magnitude(horizontalAngle(point.x, point.z, local.x, local.z, yaw));
                            if (!(ignoreFov.get() > angle)) {
                                Vec3 previous = new Vec3(player.lastX, player.lastY, player.lastZ);
                                int destination = count;
                                ++count;
                                Entry entry = entries[destination];
                                entry.currentX = point.x;
                                entry.currentZ = point.z;
                                entry.previousX = previous.x;
                                entry.previousZ = previous.z;
                                entry.currentDistance = currentDistance(squaredDistance);
                                entry.previousDistance = (float) Geometry.distance(previousLocal, previous);
                                int mode = colorMode.index();
                                if (mode == 2) {
                                    float[] color = ((tags & 5) != 0 ? colorFriendlies : colorEnemies).liveChannels();
                                    entry.color = new float[]{color[0], color[1], color[2]};
                                } else if (mode == 1) {
                                    entry.color = ScreenProjection.nameColorRgb(NameColors.entityColor(player));
                                }
                                if (count == 100) {
                                    break;
                                }
                            }
                        }
                    }
                }
                ++index;
            } while (index < list.size());
        }

        QSort.sort(entries, count, Pointers::compareDistance);
    }

    void render(ScreenRenderEvent event) {
        if (count == 0) {
            return;
        }
        ClientGameContext c = event.game();
        float partial = event.partialTick;
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        float pitch = manager.cameraPitch;
        float yaw = manager.cameraYaw;
        Primitives.beginPointerVertices();
        float centerX = event.width * 0.5f;
        float centerY = event.height * 0.5f;
        GL11.glPushMatrix();
        if (style.index() == 0) {
            GL11.glTranslatef(centerX, centerY, 0.0f);
        } else {
            float tilt = Floats.max(30.0f, pitch);
            GL11.glTranslatef(centerX, centerY, 0.0f);
            GL11.glRotatef(90.0f - tilt, 1.0f, 0.0f, 0.0f);
        }
        GL11.glLineWidth(1.0f);
        GL11.glEnable(0xb20);
        if (count != 0) {
            float[] range = {nearFarRange.low(), nearFarRange.high()};
            float width = range[1] - range[0];
            float denominator = width == 0.0f ? 0.00001f : width;
            int index = 0;
            do {
                Entry entry = entries[index];
                float x = interpolateFloat((float) entry.previousX, (float) entry.currentX, partial);
                float z = interpolateFloat((float) entry.previousZ, (float) entry.currentZ, partial);
                Vec3 camera = event.camera;
                float angle = horizontalAngle(x, z, camera.x, camera.z, yaw);
                GL11.glPushMatrix();
                GL11.glRotatef(angle, 0.0f, 0.0f, 1.0f);
                float scale = scaleSetting.get() * 10.0f;
                GL11.glScalef(scale, scale, scale);
                float distance = interpolateFloat(entry.previousDistance, entry.currentDistance, partial);
                float fraction = clampFraction(
                        (distance - nearFarRange.low()) / denominator);
                float radius = radiusSetting.get();
                if (scaleRadius.get()) {
                    radius = radius * (fraction + 1.0f);
                }
                GL11.glTranslatef(0.0f, Floats.negate(radius), 0.0f);
                if (colorMode.index() == 0) {
                    float[] near = colorNear.liveChannels();
                    float[] far = colorFar.liveChannels();
                    float red = interpolateFloat(near[0], far[0], fraction);
                    float green = interpolateFloat(near[1], far[1], fraction);
                    float blue = interpolateFloat(near[2], far[2], fraction);
                    GL11.glColor3f(red, green, blue);
                } else {
                    GL11.glColor3f(entry.color[0], entry.color[1], entry.color[2]);
                }
                Primitives.drawFilledPointer();
                GL11.glColor3f(0.0f, 0.0f, 0.0f);
                Primitives.drawPointerOutline();
                GL11.glPopMatrix();
                ++index;
            } while (index < count);
        }
        GL11.glPopMatrix();
        Primitives.endPrimitiveVertices();
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%s", style.choice(style.index()));
    }

    @Override
    protected void setup() {
        on(EventTypes.SCREEN_RENDER, e -> render(e), -200);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.show(ignoreFov);
        w.show(hideFriendlies);
        w.heading("Customization");
        w.show(style);
        w.show(colorMode);
        w.show(nearFarRange);
        if (colorMode.index() == 2) {
            w.show(colorEnemies);
            if (!hideFriendlies.get())
                w.show(colorFriendlies);
        } else if (colorMode.index() == 0) {
            w.show(colorNear);
            w.show(colorFar);
        }
        w.show(scaleSetting);
        w.show(radiusSetting);
        w.show(scaleRadius);
    }
}
