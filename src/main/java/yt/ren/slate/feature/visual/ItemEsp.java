package yt.ren.slate.feature.visual;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.GLSM;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.setting.SettingLists.ColoredItem;
import yt.ren.slate.setting.SettingLists.ColoredList;
import yt.ren.slate.text.ValueFormatter;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class ItemEsp extends Module {
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f blocks", 0.0f, 0.0f, 20.0f, 100.0f, 50.0f, 0.0f));
    private final BooleanSetting showCount = register("show_count", new BooleanSetting("Show count", 0, 0));
    private final ListSetting allowedItems = register("allowed_items", new ListSetting("Item types", "Search item", 1));
    private final BooleanSetting highlightAll = register("highlight_all", new BooleanSetting("Highlight all items", 0, 0));
    private final ColorSetting defaultColor = register("default_color", new ColorSetting("Default color", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));

    private final ClientRuntime runtime;

    private static class Entry {
        Vec3 previous = Vec3.ZERO;
        Vec3 current = Vec3.ZERO;
        float[] color = new float[3];
        String label;
        int labelWidth;
        int itemId;
        int count;
    }

    private long count;
    private final Entry[] entries = new Entry[100];

    public ItemEsp(ClientRuntime runtime) {
        super(ModuleId.ITEM_ESP, "Item ESP", "Highlights dropped items.", Category.VISUAL);
        allowedItems.attachStorage(runtime.lists.itemEspStorage());
        this.runtime = runtime;
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new Entry();
        }
    }


    private static ColoredItem find(ColoredList list, int id) {
        if (list.data == null) {
            return null;
        }
        int low = 0;
        int high = list.count - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int difference = id - list.data[middle].id;
            if (difference == 0) {
                return list.data[middle];
            }
            if (difference < 0) {
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return null;
    }

    private void clearLabels() {
        long total = count;
        if (total == 0) {
            return;
        }
        long index = 0;
        do {
            Entry entry = entries[(int) index];
            if (entry.label != null) {
                entry.label = null;
                total = count;
            }
            ++index;
        } while (Long.compareUnsigned(index, total) < 0);
    }

    private void groupLabels(TextRenderer font) {
        long index = 0;
        do {
            Entry entry = entries[(int) index];
            long total = entry.count & 0xFF;
            if (total != 0) {
                long otherIndex = 0;
                do {
                    Entry other = entries[(int) otherIndex];
                    if (otherIndex != index && entry.itemId == other.itemId) {
                        if (0.0625 > Geometry.distanceSquared(entry.current, other.current)) {
                            total += other.count & 0xFF;
                            other.count = 0;
                        }
                    }
                    ++otherIndex;
                } while (Long.compareUnsigned(otherIndex, count) < 0);
                long displayed = Long.compareUnsigned(total, 99) < 0 ? total : 99;
                entry.count = (int) displayed & 0xFF;
                if (Long.compareUnsigned(total, 2) < 0) {
                    entry.label = null;
                } else {
                    entry.label = ValueFormatter.format("%u", displayed);
                    entry.labelWidth = font.getWidth(entry.label);
                }
            }
            ++index;
        } while (Long.compareUnsigned(index, count) < 0);
    }

    void update(ClientGameContext c) {
        clearLabels();
        count = 0;
        if (c.world == null) {
            return;
        }
        Vec3 origin = new Vec3(c.player.x, c.player.y, c.player.z);
        List<Entity> list = c.world.entities;
        if (list.size() > 0) {
            int index = 0;
            do {
                Entity candidate = list.get(index);
                ItemEntity entity = candidate instanceof ItemEntity ? (ItemEntity) candidate : null;
                if (entity != null && !entity.removed) {
                    Vec3 point = new Vec3(entity.x, entity.y, entity.z);
                    double squaredDistance = Geometry.distanceSquared(point, origin);
                    float range = rangeSetting.get();
                    if (!(squaredDistance > (double) (range * range))) {
                        int destination = (int) count;
                        ItemStack stack = entity.getItem();
                        if (stack != null) {
                            int size = stack.size;
                            if (size > 0) {
                                Item item = stack.getItem();
                                if (item != null) {
                                    int id = Item.getId(item);
                                    ColoredItem found = find(runtime.lists.itemEsp, id);
                                    if (found != null || highlightAll.get()) {
                                        Entry entry = entries[destination];
                                        float[] source = found != null ? found.color
                                                : defaultColor.liveChannels();
                                        entry.color = new float[]{source[0], source[1], source[2]};
                                        entry.itemId = id;
                                        entry.count = size & 0xFF;
                                        entry.current = point;
                                        entry.previous = new Vec3(entity.lastX, entity.lastY, entity.lastZ);
                                        ++count;
                                        if (count == 100) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ++index;
            } while (index < list.size());
        }
        if (!showCount.get() || count == 0) {
            return;
        }
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        TextRenderer font = manager.textRenderer;
        if (font != null && count != 0) {
            groupLabels(font);
        }
    }

    private static Vec3 renderPosition(Entry entry, WorldRenderEvent event, double fraction) {
        Vec3 point = entry.previous.interpolate(entry.current, fraction);
        return point.subtract(event.camera);
    }

    private static void translateItem(Vec3 point) {
        GL11.glTranslatef((float) point.x, (float) (point.y + 0.3), (float) point.z);
    }

    void render(WorldRenderEvent event) {
        if (count == 0) {
            return;
        }
        GL11.glEnable(0xbe2);
        float partial = event.partialTick;
        Primitives.beginCubeVertices();
        if (count != 0) {
            double fraction = partial;
            long index = 0;
            do {
                Entry entry = entries[(int) index];
                Vec3 point = renderPosition(entry, event, fraction);
                GL11.glPushMatrix();
                translateItem(point);
                GL11.glScalef(0.3f, 0.3f, 0.3f);
                GL11.glColor4f(entry.color[0], entry.color[1], entry.color[2], 0.4f);
                Primitives.drawFilledCube();
                GL11.glPopMatrix();
                ++index;
            } while (Long.compareUnsigned(index, count) < 0);
        }
        Primitives.endPrimitiveVertices();
        if (!showCount.get()) {
            return;
        }
        ClientGameContext c = event.game();
        EntityRenderDispatcher manager = c.client.getEntityRenderDispatcher();
        TextRenderer font = manager.textRenderer;
        float pitch = manager.cameraPitch;
        float yaw = manager.cameraYaw;
        float textPartial = event.partialTick;
        if (event.managed == 1) {
            GLSM.enableTexture();
            GLSM.color(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            GL11.glEnable(0xde1);
            GL11.glColor3f(1.0f, 1.0f, 1.0f);
        }
        long total = count;
        if (total != 0) {
            double fraction = textPartial;
            float oppositeYaw = Float.intBitsToFloat(Float.floatToRawIntBits(yaw) ^ 0x80000000);
            long index = 0;
            do {
                Entry entry = entries[(int) index];
                if (entry.label != null) {
                    Vec3 point = renderPosition(entry, event, fraction);
                    GL11.glPushMatrix();
                    translateItem(point);
                    GL11.glRotatef(oppositeYaw, 0.0f, 1.0f, 0.0f);
                    GL11.glRotatef(pitch, 1.0f, 0.0f, 0.0f);
                    GL11.glScalef(-0.05f, -0.05f, -0.05f);
                    int negativeWidth = -entry.labelWidth;
                    float x = (float) ((double) negativeWidth * 0.5);
                    font.draw(entry.label, x, -4.0f, -1, true);
                    GL11.glPopMatrix();
                    total = count;
                }
                ++index;
            } while (Long.compareUnsigned(index, total) < 0);
        }
        if (event.managed == 0) {
            GL11.glDisable(0xde1);
        } else {
            GLSM.disableTexture();
        }
    }

    void clearSettings() {
        ColoredList items = runtime.lists.itemEsp;
        items.count = 0;
        items.data = null;
    }

    @Override
    protected void setup() {
        on(EventTypes.WORLD_RENDER, e -> render(e), -200);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context));
        on(EventTypes.UNLOAD, e -> clearSettings());
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.heading("Customization");
        w.show(showCount);
        w.show(allowedItems);
        w.show(highlightAll);
        if (highlightAll.get()) w.show(defaultColor);
    }
}
