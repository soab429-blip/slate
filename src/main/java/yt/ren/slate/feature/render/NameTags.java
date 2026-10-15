package yt.ren.slate.feature.render;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ScreenRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.render.GLSM;
import yt.ren.slate.render.ScreenProjection;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.text.ByteTextBuffer;
import yt.ren.slate.text.ModifiedUtf8;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.QSort;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class NameTags extends Module {
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f blocks", 0.0f, 0.0f, 20.0f, 300.0f, 150.0f, 0.0f));
    private final ScalarSetting scale = register("scale", new ScalarSetting("Scale", "%0.0f %%", 0.0f, 100.0f, 0.5f, 3.0f, 2.0f, 0.0f));
    private final BooleanSetting showOwnNameTag = register("show_own_name_tag", new BooleanSetting("Show own name tag", 0, 0));
    private final BooleanSetting useDisplayName = register("use_display_name", new BooleanSetting("Use display name format", 1, 0));
    private final BooleanSetting distanceScaling = register("distance_scaling", new BooleanSetting("Distance scaling", 0, 0));
    private final BooleanSetting background = register("background", new BooleanSetting("Text background", 1, 0));
    private final BooleanSetting textShadow = register("text_shadow", new BooleanSetting("Text shadow", 0, 0));
    private final BooleanSetting showHealth = register("show_health", new BooleanSetting("Health", 0, 0));
    private final ChoiceSetting healthFmt = register("health_fmt", new ChoiceSetting("Health format", 2, 0, new String[]{"HP (0-20)", "Hearts (0.0-10.0)"}, 0, 0, 0));
    private final BooleanSetting showDistance = register("show_distance", new BooleanSetting("Distance", 0, 0));
    private final BooleanSetting showEquipment = register("show_equipment", new BooleanSetting("Equipment", 0, 0));
    private final BooleanSetting showEnchants = register("show_enchants", new BooleanSetting("Enchantments", 0, 0));

    private static final String HEALTH_FORMAT = "§%c%.*f";
    private static final String ABSORPTION_FORMAT = "§6+%.*f";
    private static final byte[] HEALTH_SUFFIX = {(byte) 0xe2, (byte) 0x9d, (byte) 0xa4, (byte) 0xc2, (byte) 0xa7, 0x72, 0x20};
    private static final byte[] FORMAT_MARKER = {(byte) 0xc2, (byte) 0xa7};
    private static final String DISTANCE_FORMAT = "§r §%c%.0fm";
    private static final String ENCHANTMENT_FORMAT = "%s %d";
    private static final String ID_KEY = "id";
    private static final String LEVEL_KEY = "lvl";
    private static final String[] ENCHANTMENT_NAMES = {
            "Pr", "FP", "FF", "BP", "PP", "Re", "AA", "Th", "DS", null, null, null, null, null, null, null,
            "Sh", "Sm", "BA", "Kn", "FA", "Lo", null, null, null, null, null, null, null, null, null, null,
            "Ef", "ST", "Un", "Fo", null, null, null, null, null, null, null, null, null, null, null, null,
            "Po", "Pu", "Fl", "In", null, null, null, null, null, null, null, null, null, "LS", "Lu",
    };
    private static final int[] ENCHANTMENT_PRIORITY = {
            0, 3, 1, 4, 3, 4, 3, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 1, 1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 1, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
    };

    private static class Equipment {
        int present;
        int count;
        String[] labels;
    }

    private static class Entry {
        Vec3 previous;
        Vec3 current;
        PlayerEntity entity;
        String label;
        int width;
        float previousDistance;
        float currentDistance;
        int equipmentCount;
        final Equipment[] equipment = new Equipment[5];

        Entry() {
            for (int i = 0; i < equipment.length; ++i) {
                equipment[i] = new Equipment();
            }
        }
    }

    private static class Enchantment {
        short id;
        short level;
    }


    private int count;
    private final Entry[] entries = new Entry[100];
    private ItemRenderer itemRenderer;
    private final byte[] nameText = new byte[128];
    private final Enchantment[] enchantments = new Enchantment[25];
    private final byte[] enchantmentText = new byte[6];

    public NameTags(ClientRuntime runtime) {
        super(ModuleId.NAME_TAGS, "Name Tags", "Makes player name tags easier to see.", Category.VISUAL);
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
        for (int i = 0; i < enchantments.length; ++i) {
            enchantments[i] = new Enchantment();
        }
    }



    private static float clamp(float value, float upper) {
        if (value < 0.0f) {
            return 0.0f;
        }
        return upper < value ? upper : value;
    }

    private static float rootFloat(float value) {
        if (!(value >= 0.0f)) {
            return (0.0f) / 0.0f;
        }
        return (float) Math.sqrt(value);
    }

    private static double rootDouble(double value) {
        if (!(value >= 0.0)) {
            return (0.0) / 0.0;
        }
        return Math.sqrt(value);
    }

    private static int tags(PlayerEntity entity) {
        int bits = Float.floatToRawIntBits(entity.speed);
        return (bits & 0xffff0000) == 0xb3150000 ? bits & 0xffff : 0;
    }

    private static float healthValue(float value, float fallback) {
        return (Float.floatToRawIntBits(value) & 0x7fffffff) > 0x7f800000 ? fallback : value;
    }

    private String buildLabel(ClientGameContext c, PlayerEntity entity, int playerTags, double squared) {
        String name;
        if (useDisplayName.get()) {
            name = entity.getDisplayName().getFormattedString();
            if (!showHealth.get() && !showDistance.get()) {
                return name;
            }
        } else {
            name = entity.getName();
        }
        byte[] utf = ModifiedUtf8.encode(name);
        int length = utf.length;
        int limit = length < 64 ? length : 64;
        ByteTextBuffer buffer = new ByteTextBuffer(nameText, 128);
        if (showHealth.get()) {
            float health = clamp(healthValue(entity.getHealth(), 20.0f), 999.0f);
            float absorption = clamp(healthValue(entity.getAbsorption(), 0.0f), 99.0f);
            int color = health < 8.0f ? 'c' : health < 14.0f ? 'e' : 'a';
            int precision = healthFmt.index() == 1 ? 1 : 0;
            if (precision == 1) {
                health = health * 0.5f;
                absorption = absorption * 0.5f;
            }
            if (!buffer.appendFormat(HEALTH_FORMAT, color, precision, (double) health)) {
                return name;
            }
            if (absorption > 0.0f && !buffer.appendFormat(ABSORPTION_FORMAT, precision, (double) absorption)) {
                return name;
            }
            if (!buffer.appendText(HEALTH_SUFFIX)) {
                return name;
            }
        }
        if (useDisplayName.get()) {
            if (!buffer.appendText(utf)) {
                return name;
            }
        } else {
            if (!buffer.appendText(FORMAT_MARKER)) {
                return name;
            }
            if (!buffer.appendCharacter((byte) ((playerTags & 5) == 0 ? 'c' : 'a'))) {
                return name;
            }
            if (length > 0) {
                int index = 0;
                do {
                    if ((utf[index] & 0xff) == 0xc2) {
                        index += 2;
                    } else if (!buffer.appendCharacter(utf[index])) {
                        return name;
                    }
                    index += 1;
                } while (index < limit);
            }
        }
        if (showDistance.get() && c.player != entity) {
            double distance = rootDouble(squared);
            float rounded = (float) distance;
            int color = rounded < 10.0f ? 'c' : rounded < 20.0f ? 'e' : 'a';
            if (!buffer.appendFormat(DISTANCE_FORMAT, color, distance)) {
                return name;
            }
        }
        return buffer.finish() ? ModifiedUtf8.decode(nameText, 128) : name;
    }

    private void collectEnchantments(PlayerEntity entity, Entry entry) {
        for (int slot = 0; slot < 5; ++slot) {
            Equipment destination = entry.equipment[slot];
            destination.labels = null;
            destination.count = 0;
            ItemStack stack = entity.getEquipment(slot);
            if (stack == null) {
                continue;
            }
            NbtList list = stack.getEnchantments();
            if (list == null) {
                continue;
            }
            int originalCount = list.size();
            int count = originalCount < 25 ? originalCount : 25;
            if (originalCount > 0) {
                for (int index = 0; index < count; ++index) {
                    NbtCompound tag = list.getCompound(index);
                    int id = tag.getShort(ID_KEY) & 0xffff;

                    if (id > 62 || ((0x1ff0fff0ffc0fe00L >>> id) & 1L) != 0) {
                        continue;
                    }
                    short level = tag.getShort(LEVEL_KEY);
                    if (level <= 0) {
                        level = 0;
                    }
                    if (level >= 99) {
                        level = 99;
                    }
                    enchantments[index].id = (short) id;
                    enchantments[index].level = level;
                }
            }
            QSort.sort(enchantments, count, NameTags::compareEnchantment);
            int shown = originalCount < 4 ? originalCount : 4;
            destination.labels = new String[shown];
            if (originalCount > 0) {
                for (int index = 0; index < shown; ++index) {
                    Enchantment enchantment = enchantments[index];
                    int id = enchantment.id & 0xffff;
                    String name = id < 63 ? ENCHANTMENT_NAMES[id] : null;
                    ByteTextBuffer buffer = new ByteTextBuffer(enchantmentText, 6);
                    buffer.appendFormat(ENCHANTMENT_FORMAT, name, (int) enchantment.level);
                    destination.labels[index] = ModifiedUtf8.decode(enchantmentText, 6);
                    destination.count += 1;
                }
            }
        }
    }

    private static int compareDistance(Entry first, Entry second) {
        return Sse.cvttss2si(second.currentDistance - first.currentDistance);
    }

    private static int compareEnchantment(Enchantment first, Enchantment second) {
        return ENCHANTMENT_PRIORITY[first.id] - ENCHANTMENT_PRIORITY[second.id];
    }

    private void clear() {
        if (count != 0) {
            int index = 0;
            do {
                Entry entry = entries[index];
                for (Equipment slot : entry.equipment) {
                    if (slot.labels != null) {
                        slot.labels = null;
                    }
                }
                ++index;
            } while (index < count);
        }
        count = 0;
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.state != 0 || event.module != descriptor) {
            return;
        }
        clear();
        if (itemRenderer != null) {
            itemRenderer = null;
        }
    }

    void update(ClientGameContext c) {
        clear();
        if (c.world == null) {
            return;
        }
        if (itemRenderer == null) {
            itemRenderer = c.client.itemRenderer;
        }
        Vec3 local = new Vec3(c.player.x, c.player.y, c.player.z);
        Vec3 previousLocal = new Vec3(c.player.lastX, c.player.lastY, c.player.lastZ);
        List<PlayerEntity> list = c.world.players;
        TextRenderer font = c.client.getEntityRenderDispatcher().textRenderer;
        if (font == null) {
            return;
        }
        if (list.size() > 0) {
            int index = 0;
            do {
                PlayerEntity entity = list.get(index);
                boolean own = entity == c.player;
                boolean selected = !own;
                if (own && showOwnNameTag.get()) {
                    selected = c.client.options.perspective != 0;
                }
                if (selected) {
                    int playerTags = tags(entity);
                    if ((playerTags & 2) == 0 && !entity.removed) {
                        Vec3 current = new Vec3(entity.x, entity.y, entity.z);
                        double squared = Geometry.distanceSquared(current, local);
                        float range = rangeSetting.get();
                        if (!(squared > (double) (range * range))) {
                            String label = buildLabel(c, entity, playerTags, squared);
                            Vec3 previous = new Vec3(entity.lastX, entity.lastY, entity.lastZ);
                            int destination = count;
                            ++count;
                            Entry entry = entries[destination];
                            entry.current = current;
                            entry.previous = previous;
                            entry.entity = entity;
                            entry.currentDistance = rootFloat((float) squared);
                            entry.previousDistance = (float) Geometry.distance(previousLocal, entry.previous);
                            entry.label = label;
                            entry.width = font.getWidth(label);
                            entry.equipmentCount = 0;
                            for (int slot = 0; slot < 5; ++slot) {
                                ItemStack stack = entity.getEquipment(slot);
                                if (stack != null) {
                                    entry.equipmentCount += 1;
                                }
                                entry.equipment[slot].present = stack != null ? 1 : 0;
                            }
                            if (showEnchants.get()) {
                                collectEnchantments(entity, entry);
                            }
                            if (count == 100) {
                                break;
                            }
                        }
                    }
                }
                ++index;
            } while (index < list.size());
        }
        QSort.sort(entries, count, NameTags::compareDistance);
    }

    void render(ScreenRenderEvent event) {
        if (count == 0 || itemRenderer == null) {
            return;
        }
        ClientGameContext c = event.game();
        TextRenderer font = c.client.getEntityRenderDispatcher().textRenderer;
        float savedDepth = itemRenderer.zOffset;
        itemRenderer.zOffset = -200.0f;
        float fov = c.client.gameRenderer.getFov(event.partialTick, true);
        boolean managed = event.managed == 1;
        if (count != 0) {
            float fovScale = 180.0f / fov;
            int index = 0;
            float[] screen = new float[2];
            float[] depth = new float[1];
            do {
                Entry entry = entries[index];
                double partial = event.partialTick;
                double px = (entry.current.x - entry.previous.x) * partial + entry.previous.x;
                double py = (entry.current.y - entry.previous.y) * partial + entry.previous.y;
                double pz = (entry.current.z - entry.previous.z) * partial + entry.previous.z;
                py = py + 2.2;
                Vec3 camera = event.camera;
                Vec3 relative = new Vec3(px - camera.x, py - camera.y, pz - camera.z);
                if (ScreenProjection.project(event, relative, screen, depth)) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(screen[0], screen[1], depth[0] * 100.0f);
                    float distanceScale = depth[0] * 10.0f;
                    float zoom = distanceScale > 1.0f ? distanceScale : 1.0f;
                    if (!distanceScaling.get()) {
                        zoom = distanceScale;
                    }
                    float size = scale.get() * fovScale * zoom;
                    GL11.glScalef(size, size, size);
                    int negativeWidth = -entry.width;
                    double leftDouble = (double) negativeWidth * 0.5 + -2.0;
                    if (managed) {
                        GLSM.disableDepth();
                    } else {
                        GL11.glDisable(0xb71);
                    }
                    float left = (float) leftDouble;
                    if (background.get()) {
                        if (managed) {
                            GLSM.disableTexture();
                            GLSM.color(0.0f, 0.0f, 0.0f, 0.5f);
                        } else {
                            GL11.glDisable(0xde1);
                            GL11.glColor4f(0.0f, 0.0f, 0.0f, 0.5f);
                        }
                        float right = (float) ((double) entry.width * 0.5 + 2.0);
                        GL11.glBegin(5);
                        GL11.glVertex2f(left, 0.0f);
                        GL11.glVertex2f(right, 0.0f);
                        GL11.glVertex2f(left, -12.0f);
                        GL11.glVertex2f(right, -12.0f);
                        GL11.glEnd();
                    }
                    if (managed) {
                        GLSM.enableTexture();
                        GLSM.color(1.0f, 1.0f, 1.0f, 1.0f);
                    } else {
                        GL11.glEnable(0xde1);
                        GL11.glColor3f(1.0f, 1.0f, 1.0f);
                    }
                    font.draw(entry.label, left + 2.0f, -10.0f, -1, textShadow.get());
                    if (showEquipment.get()) {
                        if (managed) {
                            GLSM.enableDepth();
                        } else {
                            GL11.glEnable(0xb71);
                        }
                        Lighting.turnOn();
                        GL11.glScalef(1.0f, 1.0f, Float.intBitsToFloat(0x3a83126f));
                        int x = -(entry.equipmentCount << 3);
                        for (int slot = 0; slot < 5; ++slot) {
                            if (entry.equipment[slot].present != 1) {
                                continue;
                            }
                            ItemStack stack = entry.entity.getEquipment(slot);
                            if (stack == null) {
                                continue;
                            }
                            itemRenderer.renderGuiItem(stack, x, -32);
                            itemRenderer.renderGuiItemDecorations(font, stack, x, -32, null);
                            x += 16;
                        }
                        Lighting.turnOff();
                        if (!managed) {
                            GL11.glEnable(0xbe2);
                            GL11.glDisable(0xbc0);
                            GL11.glBlendFunc(0x302, 0x303);
                        } else {
                            GLSM.enableBlend();
                            GLSM.disableAlpha();
                            GLSM.blendFunc(0x302, 0x303);
                        }
                        GL11.glColor3f(1.0f, 1.0f, 1.0f);
                        GL11.glScalef(0.5f, 0.5f, 0.5f);
                        if (showEnchants.get()) {
                            x = -(entry.equipmentCount << 3);
                            for (Equipment slot : entry.equipment) {
                                if (slot.present != 1) {
                                    continue;
                                }
                                if (slot.labels != null && slot.count > 0) {
                                    float textX = (float) (x * 2);
                                    int y = -64;
                                    int label = 0;
                                    do {
                                        font.draw(slot.labels[label], textX, (float) y, -1, true);
                                        ++label;
                                        y += 8;
                                    } while (label < slot.count);
                                }
                                x += 16;
                            }
                        }
                    }
                    GL11.glPopMatrix();
                }
                ++index;
            } while (index < count);
        }
        if (!managed) {
            GL11.glDisable(0xde1);
            GL11.glDisable(0xb71);
        } else {
            GLSM.disableTexture();
            GLSM.disableDepth();
        }
        itemRenderer.zOffset = savedDepth;
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.SCREEN_RENDER, e -> render(e), -1000);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.show(scale);
        w.show(showOwnNameTag);
        w.show(useDisplayName);
        w.show(distanceScaling);
        w.show(background);
        w.show(textShadow);
        w.heading("Extra info");
        w.show(showHealth);
        if (showHealth.get()) w.show(healthFmt);
        w.show(showDistance);
        w.show(showEquipment);
        if (showEquipment.get()) w.show(showEnchants);
    }
}
