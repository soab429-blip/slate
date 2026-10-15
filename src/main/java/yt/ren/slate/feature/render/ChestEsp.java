package yt.ren.slate.feature.render;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.render.Primitives;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Vec3;

import java.util.List;

public class ChestEsp extends Module {
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f blocks", 0.0f, 0.0f, 10.0f, 100.0f, 20.0f, 0.0f));
    private final BooleanSetting renderChest = register("render_chest", new BooleanSetting("Regular chests", 1, 0));
    private final BooleanSetting renderEnderChest = register("render_ender_chest", new BooleanSetting("Ender chests", 1, 0));
    private final BooleanSetting renderTrappedChest = register("render_trapped_chest", new BooleanSetting("Trapped chests", 1, 0));
    private final BooleanSetting renderBeacon = register("render_beacon", new BooleanSetting("Beacons", 1, 0));
    private final ScalarSetting lineWidth = register("line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));
    private final ColorSetting colorChest = register("color_chest", new ColorSetting("Regular chests", 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorEnderChest = register("color_ender_chest", new ColorSetting("Ender chests", 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorTrappedChest = register("color_trapped_chest", new ColorSetting("Trapped chests", 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    private final ColorSetting colorBeacon = register("color_beacon", new ColorSetting("Beacons", 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f));
    private final BooleanSetting filled = register("filled", new BooleanSetting("Filled", 1, 0));


    private int count;
    private final Vec3[] positions = new Vec3[100];
    private final int[] blockIds = new int[100];

    public ChestEsp(ClientRuntime runtime) {
        super(ModuleId.CHEST_ESP, "Chest ESP", "Highlights chests.", Category.VISUAL);
    }


    private boolean selected(int id) {
        switch (id) {
            case 54:
                return renderChest.get();
            case 130:
                return renderEnderChest.get();
            case 146:
                return renderTrappedChest.get();
            case 138:
                return renderBeacon.get();
            default:
                return false;
        }
    }

    private float[] colorFor(int id) {
        switch (id) {
            case 130:
                return colorEnderChest.liveChannels();
            case 138:
                return colorBeacon.liveChannels();
            case 146:
                return colorTrappedChest.liveChannels();
            default:
                return colorChest.liveChannels();
        }
    }

    static Vec3 tilePosition(BlockEntity tile) {
        BlockPos position = tile.pos;
        return new Vec3(position.getX(), position.getY(), position.getZ());
    }

    void update(ClientGameContext c) {
        count = 0;
        if (c.world == null) {
            return;
        }
        Vec3 origin = new Vec3(c.player.x, c.player.y, c.player.z);
        List<BlockEntity> list = c.world.blockEntities;
        if (list.size() <= 0) {
            return;
        }
        int index = 0;
        do {
            BlockEntity tile = list.get(index);
            Block block = tile.block;
            if (block != null) {
                int id = Block.getId(block);
                if (selected(id)) {
                    Vec3 point = tilePosition(tile);
                    double squaredDistance = Geometry.distanceSquared(point, origin);
                    float range = rangeSetting.get();

                    if (!(squaredDistance > (double) (range * range))) {
                        int old = count;
                        count = old + 1;
                        positions[old] = point;
                        blockIds[old] = id;
                        if (count == 100) {
                            return;
                        }
                    }
                }
            }
            ++index;
        } while (index < list.size());
    }

    void render(WorldRenderEvent event) {
        Primitives.beginCubeVertices();
        GL11.glLineWidth(lineWidth.get());
        if (count != 0) {
            int index = 0;
            do {
                float[] color = colorFor(blockIds[index]);
                Vec3 relative = positions[index].subtract(event.camera);
                Vec3 center = relative.add(new Vec3(0.5, 0.5, 0.5));
                GL11.glPushMatrix();
                GL11.glTranslatef((float) center.x, (float) center.y, (float) center.z);
                GL11.glScalef(0.5f, 0.5f, 0.5f);
                if (filled.get()) {
                    GL11.glColor4f(color[0], color[1], color[2], 0.4f);
                    Primitives.drawFilledCube();
                }
                GL11.glColor3f(color[0], color[1], color[2]);
                Primitives.drawCubeOutline();
                GL11.glPopMatrix();
                ++index;
            } while (index < count);
        }
        Primitives.endPrimitiveVertices();
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update((ClientGameContext) e.context));
        on(EventTypes.WORLD_RENDER, e -> render(e));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.heading("Target");
        w.show(renderChest);
        w.show(renderEnderChest);
        w.show(renderTrappedChest);
        w.show(renderBeacon);
        w.heading("Customization");
        w.show(lineWidth);
        if (renderChest.get()) w.show(colorChest);
        if (renderEnderChest.get())
            w.show(colorEnderChest);
        if (renderTrappedChest.get())
            w.show(colorTrappedChest);
        if (renderBeacon.get()) w.show(colorBeacon);
        w.show(filled);
    }
}
