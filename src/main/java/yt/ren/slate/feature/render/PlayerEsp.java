package yt.ren.slate.feature.render;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.game.NameColors;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.render.ScreenProjection;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.QSort;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class PlayerEsp extends Module {
    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Style", 2, 0, new String[]{"2D", "3D"}, 0, 0, 0));
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f blocks", 0.0f, 0.0f, 5.0f, 300.0f, 150.0f, 0.0f));
    private final BooleanSetting hideFriendlies = register("hide_friendlies", new BooleanSetting("Hide friendlies", 0, 0));
    private final ChoiceSetting colorMode = register("color_mode", new ChoiceSetting("Colors", 2, 0, new String[]{"Automatic", "Manual"}, 0, 0, 0));
    private final ColorSetting colorEnemies = register("color_enemies", new ColorSetting("Enemy color", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorFriendlies = register("color_friendlies", new ColorSetting("Friendly color", 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final BooleanSetting overrideTargetColor = register("override_target_color", new BooleanSetting("Override target color", 0, 0));
    private final ColorSetting colorTarget = register("color_target", new ColorSetting("Target color", 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ScalarSetting lineWidth = register("line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));
    private final BooleanSetting hurtEffect = register("hurt_effect", new BooleanSetting("Hurt effect", 0, 0));
    private final BooleanSetting filled = register("filled", new BooleanSetting("Filled", 1, 0));
    private final BooleanSetting show2dBox = register("show_2d_box", new BooleanSetting("Show box", 1, 0));
    private final BooleanSetting showHealthBar = register("show_health_bar", new BooleanSetting("Show health bar", 1, 0));

    private static final Vec3[] CORNERS = {
            corner(0xbfd999999999999aL, 0x8000000000000000L, 0xbfd999999999999aL),
            corner(0xbfd999999999999aL, 0x8000000000000000L, 0x3fd999999999999aL),
            corner(0x3fd999999999999aL, 0x8000000000000000L, 0xbfd999999999999aL),
            corner(0x3fd999999999999aL, 0x8000000000000000L, 0x3fd999999999999aL),
            corner(0xbfd999999999999aL, 0x4000000000000000L, 0xbfd999999999999aL),
            corner(0xbfd999999999999aL, 0x4000000000000000L, 0x3fd999999999999aL),
            corner(0x3fd999999999999aL, 0x4000000000000000L, 0xbfd999999999999aL),
            corner(0x3fd999999999999aL, 0x4000000000000000L, 0x3fd999999999999aL),
    };

    private static Vec3 corner(long x, long y, long z) {
        return new Vec3(Double.longBitsToDouble(x), Double.longBitsToDouble(y), Double.longBitsToDouble(z));
    }

    private final ClientRuntime runtime;

    private static class Entry {
        Vec3 previous = Vec3.ZERO;
        Vec3 current = Vec3.ZERO;
        float[] color = new float[3];
        float squaredDistance;
        int hurtTime;
        float healthFraction;
        float absorptionFraction;
        int target;
    }

    private int count;
    private final Entry[] entries = new Entry[100];

    public PlayerEsp(ClientRuntime runtime) {
        super(ModuleId.PLAYER_ESP, "Player ESP", "Highlights other players.", Category.VISUAL);
        this.runtime = runtime;
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

    private static float healthValue(float value, float fallback) {
        return (Float.floatToRawIntBits(value) & 0x7fffffff) > 0x7f800000 ? fallback : value;
    }

    private float[] renderColor(Entry entry, float partial) {
        float[] color = {entry.color[0], entry.color[1], entry.color[2]};
        if (entry.hurtTime > 0 && hurtEffect.get()) {
            float duration = (float) entry.hurtTime;
            float remaining = duration - partial;
            float amount = Floats.min(10.0f, remaining) * 0.1f;
            color[0] = interpolateFloat(color[0], 1.0f, amount);
            color[1] = interpolateFloat(color[1], 0.0f, amount);
            color[2] = interpolateFloat(color[2], 0.0f, amount);
        }
        return color;
    }

    private static int compareDistance(Entry first, Entry second) {
        return Sse.cvttss2si(second.squaredDistance - first.squaredDistance);
    }

    void update(ClientGameContext c) {
        if (c.world == null) {
            return;
        }
        count = 0;
        Vec3 local = new Vec3(c.player.x, c.player.y, c.player.z);
        List<PlayerEntity> list = c.world.players;
        int hide = (hideFriendlies.get() ? 1 : 0);
        if (list.size() > 0) {
            int excluded = hide * 5 + 2;
            int index = 0;
            do {
                PlayerEntity entity = list.get(index);
                if (entity != c.player) {
                    int tags = PlayerTags.tags(entity);
                    if ((tags & excluded) == 0 && !entity.removed) {
                        Vec3 current = new Vec3(entity.x, entity.y, entity.z);
                        double squared = Geometry.distanceSquared(current, local);
                        float range = rangeSetting.get();
                        if (!(squared > (double) (range * range))) {
                            float[] color = null;
                            int target = 0;
                            if (overrideTargetColor.get()
                                    && entity == runtime.aimTargetReference) {
                                color = colorTarget.liveChannels();
                                target = 1;
                            }
                            if (target == 0) {
                                if (colorMode.index() == 0) {
                                    color = ScreenProjection.nameColorRgb(NameColors.entityColor(entity));
                                } else {
                                    color = ((tags & 5) != 0 ? colorFriendlies : colorEnemies).liveChannels();
                                }
                            }
                            int destination = count;
                            ++count;
                            Entry entry = entries[destination];
                            entry.current = current;
                            entry.previous = new Vec3(entity.lastX, entity.lastY, entity.lastZ);
                            entry.color = new float[]{color[0], color[1], color[2]};
                            entry.squaredDistance = (float) squared;
                            entry.hurtTime = entity.damagedTimer;
                            entry.target = target;
                            float health = healthValue(entity.getHealth(), 20.0f);
                            float rawAbsorption = healthValue(entity.getAbsorption(), 0.0f);
                            float absorption = 0.0f > rawAbsorption ? 0.0f : rawAbsorption;
                            float maximum = healthValue(entity.getMaxHealth(), 20.0f) + absorption;
                            entry.healthFraction = maximum != 0.0f ? clampFraction((health + absorption) / maximum) : 0.0f;
                            entry.absorptionFraction = health != 0.0f ? clampFraction(absorption / health) : 0.0f;
                            if (count == 100) {
                                break;
                            }
                        }
                    }
                }
                ++index;
            } while (index < list.size());
        }
        QSort.sort(entries, count, PlayerEsp::compareDistance);
    }

    private static void drawHealth(Entry entry, float x, float top, float bottom) {
        float level = interpolateFloat(bottom, top, entry.healthFraction);
        GL11.glColor3f(0.0f, 0.0f, 0.0f);
        GL11.glBegin(1);
        GL11.glVertex2f(x, top);
        GL11.glVertex2f(x, level);
        GL11.glEnd();
        if (entry.absorptionFraction > 0.0f) {
            float next = interpolateFloat(level, bottom, entry.absorptionFraction);
            GL11.glColor3f(1.0f, 0.67f, 0.0f);
            GL11.glBegin(1);
            GL11.glVertex2f(x, level);
            GL11.glVertex2f(x, next);
            GL11.glEnd();
            level = next;
        }
        float red = interpolateFloat(1.0f, 0.0f, entry.healthFraction);
        float green = interpolateFloat(0.0f, 1.0f, entry.healthFraction);
        GL11.glColor3f(red, green, 0.0f);
        GL11.glBegin(1);
        GL11.glVertex2f(x, level);
        GL11.glVertex2f(x, bottom);
        GL11.glEnd();
    }

    void renderWorld(WorldRenderEvent event) {
        if (count == 0 || mode.index() != 1) {
            return;
        }
        GL11.glEnable(0xbe2);
        GL11.glLineWidth(lineWidth.get());
        float partial = event.partialTick;
        ClientGameContext c = event.game();
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        float yaw = manager.cameraYaw;
        if (count == 0) {
            return;
        }
        int index = 0;
        do {
            Entry entry = entries[index];
            Vec3 point = entry.previous.interpolate(entry.current, partial);
            Vec3 relative = point.subtract(event.camera);
            GL11.glPushMatrix();
            GL11.glTranslatef((float) relative.x, (float) (relative.y + 1.0), (float) relative.z);
            GL11.glScalef(0.4f, 1.0f, 0.4f);
            if (show2dBox.get() || entry.target == 1) {
                Primitives.beginCubeVertices();
                float[] color = renderColor(entry, partial);
                if (filled.get()) {
                    GL11.glColor4f(color[0], color[1], color[2], 0.2f);
                    Primitives.drawFilledCube();
                }
                GL11.glColor3f(color[0], color[1], color[2]);
                Primitives.drawCubeOutline();
                Primitives.endPrimitiveVertices();
            }
            if (showHealthBar.get()) {
                GL11.glRotatef(Floats.negate(yaw), 0.0f, 1.0f, 0.0f);
                GL11.glColor4f(0.0f, 1.0f, 0.0f, 0.75f);
                drawHealth(entry, 2.0f, 1.0f, -1.0f);
            }
            GL11.glPopMatrix();
            ++index;
        } while (index < count);
    }

    private static boolean inside(float[] point, ScreenRenderEvent event) {
        return !(0.0f > point[0]) && !(0.0f > point[1]) && !(point[0] > event.width) && !(point[1] > event.height);
    }

    void renderScreen(ScreenRenderEvent event) {
        if (count == 0 || mode.index() != 0) {
            return;
        }
        GL11.glEnable(0xbe2);
        GL11.glLineWidth(lineWidth.get());
        if (count == 0) {
            return;
        }
        float partial = event.partialTick;
        int index = 0;
        do {
            Entry entry = entries[index];
            Vec3 point = entry.previous.interpolate(entry.current, partial);
            Vec3 relative = point.subtract(event.camera);
            float[] minimum = new float[2];
            if (ScreenProjection.project(event, relative.add(CORNERS[0]), minimum, null)) {
                float[] maximum = {minimum[0], minimum[1]};
                boolean complete = true;
                for (int corner = 1; corner < 8; ++corner) {
                    float[] projected = new float[2];
                    if (!ScreenProjection.project(event, relative.add(CORNERS[corner]), projected, null)) {
                        complete = false;
                        break;
                    }
                    if (minimum[0] > projected[0]) {
                        minimum[0] = projected[0];
                    } else if (projected[0] > maximum[0]) {
                        maximum[0] = projected[0];
                    }
                    if (minimum[1] > projected[1]) {
                        minimum[1] = projected[1];
                    } else if (projected[1] > maximum[1]) {
                        maximum[1] = projected[1];
                    }
                }
                if (complete && (inside(minimum, event) || inside(maximum, event))) {
                    float[] color = renderColor(entry, partial);
                    if (show2dBox.get() || entry.target == 1) {
                        Primitives.beginQuadVertices();
                        GL11.glPushMatrix();
                        GL11.glTranslatef(minimum[0], minimum[1], 0.0f);
                        GL11.glScalef(maximum[0] - minimum[0], maximum[1] - minimum[1], 1.0f);
                        if (filled.get()) {
                            GL11.glColor4f(color[0], color[1], color[2], 0.25f);
                            Primitives.drawFilledQuad();
                        }
                        GL11.glColor3f(color[0], color[1], color[2]);
                        Primitives.drawQuadOutline();
                        GL11.glPopMatrix();
                        Primitives.endPrimitiveVertices();
                    }
                    if (showHealthBar.get()) {
                        float x = ((minimum[0] - lineWidth.get()) + -1.0f) + -4.0f;
                        drawHealth(entry, x, minimum[1], maximum[1]);
                    }
                }
            }
            ++index;
        } while (index < count);
    }

    @Override
    public String suffix() {
        return ValueFormatter.format("%s", mode.choice(mode.index()));
    }

    @Override
    protected void setup() {
        on(EventTypes.WORLD_RENDER, e -> renderWorld(e), -300);
        on(EventTypes.SCREEN_RENDER, e -> renderScreen(e));
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.show(hideFriendlies);
        w.heading("Customization");
        w.show(mode);
        w.show(colorMode);
        if (colorMode.index() == 1) {
            w.show(colorEnemies);
            if (!hideFriendlies.get())
                w.show(colorFriendlies);
        }
        w.show(overrideTargetColor);
        if (overrideTargetColor.get())
            w.show(colorTarget);
        w.show(lineWidth);
        w.show(show2dBox);
        if (show2dBox.get()) {
            w.show(filled);
            w.show(hurtEffect);
        }
        w.show(showHealthBar);
    }
}
