package yt.ren.slate.feature.visual;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.render.DrawList;
import yt.ren.slate.render.GLSM;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.TextBuffer;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.DoubleTrig;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class Indicators extends Module {
    private final ScalarSetting scaleSetting = register("scale", new ScalarSetting("Scale", "%.0f%%", 0.0f, 50.0f, 2.0f, 6.0f, 2.0f, 0.0f));
    private final ScalarSetting radius = register("radius", new ScalarSetting("Radius", "%.0f", 1.0f, 0.0f, 50.0f, 200.0f, 50.0f, 0.0f));
    private final BooleanSetting showArrow = register("show_arrow", new BooleanSetting("Arrows", 1, 0));
    private final BooleanSetting showFireball = register("show_fireball", new BooleanSetting("Fireballs", 1, 0));
    private final BooleanSetting showPearl = register("show_pearl", new BooleanSetting("Ender pearls", 1, 0));
    private final BooleanSetting requireComingCloser = register("require_coming_closer", new BooleanSetting("Coming closer", 0, 0));

    private static final float[] RED = {
            Float.intBitsToFloat(0x3f800000), 0.0f, Float.intBitsToFloat(0x3f800000), 0.0f, Float.intBitsToFloat(0x3e428f5c),
    };
    private static final float[] GREEN = {
            Float.intBitsToFloat(0x3f800000), 0.0f, Float.intBitsToFloat(0x3f000000), 0.0f, Float.intBitsToFloat(0x3f19999a),
    };
    private static final float[] BLUE = {
            Float.intBitsToFloat(0x3f800000), 0.0f, 0.0f, 0.0f, Float.intBitsToFloat(0x3f0a3d71),
    };

    private final ClientRuntime runtime;

    private static class Entry {
        double currentX;
        double currentZ;
        double previousX;
        double previousZ;
        int entityType;
        String label;
        int labelWidth;
        int textColor;
    }

    private int count;
    private final Entry[] entries = new Entry[10];

    public Indicators(ClientRuntime runtime) {
        super(ModuleId.INDICATORS, "Indicators", "Points to incoming projectiles.", Category.VISUAL);
        this.runtime = runtime;
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
    }



    private static float interpolateFloat(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static double root(double squared) {
        if (!(squared >= 0.0)) {
            return (0.0) / 0.0;
        }
        return Math.sqrt(squared);
    }

    private boolean selected(Entity entity, int type) {
        if (type == 14) {
            return showPearl.get();
        }
        if (type == 12) {
            return showFireball.get();
        }
        if (type != 10 || !showArrow.get()) {
            return false;
        }
        return !((ArrowEntity) entity).inGround;
    }

    private void clearLabels() {
        count = 0;
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.state != 0 || event.module != descriptor) {
            return;
        }
        clearLabels();
    }

    void update(ClientGameContext c) {
        clearLabels();
        if (c.world == null) {
            return;
        }
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        TextRenderer font = manager.textRenderer;
        if (font == null) {
            return;
        }
        Vec3 local = new Vec3(c.player.x, c.player.y, c.player.z);
        List<Entity> list = c.world.entities;
        if (list.size() <= 0) {
            return;
        }
        int index = 0;
        do {
            Entity entity = list.get(index);
            int type = Entities.getId(entity);
            if (selected(entity, type)) {
                Vec3 point = new Vec3(entity.x, entity.y, entity.z);
                Vec3 previous = new Vec3(entity.lastX, entity.lastY, entity.lastZ);
                double squared = Geometry.distanceSquared(point, local);
                if (!requireComingCloser.get()
                        || !(squared > Geometry.distanceSquared(previous, local))) {
                    double distance = root(squared);
                    int destination = count;
                    ++count;
                    Entry entry = entries[destination];
                    entry.currentX = point.x;
                    entry.currentZ = point.z;
                    entry.previousX = previous.x;
                    entry.previousZ = previous.z;
                    entry.entityType = type;
                    entry.label = TextBuffer.format(10, "%.0fm", distance);
                    int width = font.getWidth(entry.label);
                    int color = distance > 15.0 ? 0xffffff00 : 0xffff0000;
                    entry.labelWidth = width;
                    if (distance > 30.0) {
                        color = 0xffffffff;
                    }
                    entry.textColor = color;
                    if (count == 10) {
                        return;
                    }
                }
            }
            ++index;
        } while (index < list.size());
    }

    void render(ScreenRenderEvent event) {
        if (count == 0) {
            return;
        }
        ClientGameContext c = event.game();
        float partial = event.partialTick;
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        TextRenderer font = manager.textRenderer;
        float yaw = manager.cameraYaw;
        DrawList draw = event.draw;
        float centerX = event.width * 0.5f;
        float centerY = event.height * 0.5f;
        draw.push();
        draw.translate(centerX, centerY, 0.0f);
        draw.lineWidth(1.0f);
        double pulse = DoubleTrig.sine(runtime.clock.now() * 20.0);
        if (count != 0) {
            float alpha = (float) (pulse * 0.5 + 0.5);
            int index = 0;
            do {
                Entry entry = entries[index];
                float x = interpolateFloat((float) entry.previousX, (float) entry.currentX, partial);
                float z = interpolateFloat((float) entry.previousZ, (float) entry.currentZ, partial);
                Vec3 camera = event.camera;
                double dx = (double) x - camera.x;
                double dz = (double) z - camera.z;
                double degrees = CrtMath.atan2(dz, dx) * 180.0 / 3.141592653589793 - 90.0;
                float angle = Geometry.wrapYaw((float) degrees - yaw);
                draw.push();
                draw.rotate(angle, 0.0f, 0.0f, 1.0f);
                float scale = scaleSetting.get();
                draw.translate(0.0f, Floats.negate(radius.get()), 0.0f);
                draw.scale(scale, scale, scale);
                if (entry.label != null) {
                    draw.push();
                    draw.rotate(Floats.negate(angle), 0.0f, 0.0f, 1.0f);
                    draw.beginExternal();
                    if (event.managed == 1) {
                        GLSM.enableTexture();
                        GLSM.color(1.0f, 1.0f, 1.0f, 1.0f);
                    } else {
                        GL11.glEnable(0xde1);
                    }
                    int negativeWidth = -entry.labelWidth;
                    float textX = (float) ((double) negativeWidth * 0.5);
                    font.draw(entry.label, textX, -5.0f, entry.textColor, true);
                    if (event.managed == 1) {
                        GLSM.disableTexture();
                    } else {
                        GL11.glDisable(0xde1);
                    }
                    draw.endExternal();
                    draw.pop();
                }
                draw.translate(0.0f, -15.0f, 0.0f);
                draw.scale(5.0f, 5.0f, 5.0f);
                int colorIndex = entry.entityType - 10;
                draw.color(RED[colorIndex], GREEN[colorIndex], BLUE[colorIndex], alpha);
                draw.fillPointer();
                draw.color(0.0f, 0.0f, 0.0f, alpha);
                draw.outlinePointer();
                draw.pop();
                ++index;
            } while (index < count);
        }
        draw.pop();
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.SCREEN_RENDER, e -> render(e), -300);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(scaleSetting);
        w.show(radius);
        w.heading("Warn for");
        w.show(showArrow);
        w.show(showFireball);
        w.show(showPearl);
        w.heading("Conditions");
        w.show(requireComingCloser);
    }
}
