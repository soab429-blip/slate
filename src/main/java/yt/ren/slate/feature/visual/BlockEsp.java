package yt.ren.slate.feature.visual;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WorldChunkSection;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.BlockChangeEvent;
import yt.ren.slate.event.Events.WorldRenderEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.render.DrawList;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.setting.SettingLists.ColoredItem;
import yt.ren.slate.setting.SettingLists.ColoredList;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;

import java.util.ArrayList;
import java.util.List;

public class BlockEsp extends Module {
    private final ScalarSetting rangeSetting = register("range", new ScalarSetting("Range", "%0.0f chunk(s)", 1.0f, 0.0f, 1.0f, 10.0f, 2.0f, 0.0f));
    private final ListSetting allowedBlocks = register("allowed_blocks", new ListSetting("Block types", "Search block", 1));
    private final BooleanSetting shouldLimitBlockRender = register("should_limit_block_render", new BooleanSetting("Enabled", 0, 0));
    private final ScalarSetting blockRenderLimit = register("block_render_limit", new ScalarSetting("Limit per chunk section", "%0.0f block(s)", 1.0f, 0.0f, 1.0f, 100.0f, 100.0f, 0.0f));
    private final ScalarSetting lineWidth = register("line_width", new ScalarSetting("Line width", "%0.0f px", 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f));

    private static final float[] VERTICES = {
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
    };
    private static final int[] INDICES = {0, 1, 1, 5, 5, 4, 4, 0, 2, 3, 3, 7, 7, 6, 6, 2, 0, 2, 1, 3, 4, 6, 5, 7};

    private final ClientRuntime runtime;

    private static class Block {
        final int x;
        final int y;
        final int z;
        ColoredItem style;

        Block(int x, int y, int z, ColoredItem style) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.style = style;
        }
    }

    private static class Chunk {
        final long key;
        final List<Block> blocks = new ArrayList<>();
        float[] vertices = new float[0];
        int[] colors = new int[0];
        short[] indices = new short[0];
        int indexCount;

        Chunk(long key) {
            this.key = key;
        }
    }

    private final List<Chunk> cache = new ArrayList<>();
    private int modified;

    public BlockEsp(ClientRuntime runtime) {
        super(ModuleId.BLOCK_ESP, "Block ESP", "Highlights blocks.", Category.VISUAL);
        allowedBlocks.attachStorage(runtime.lists.blockEspStorage());
        this.runtime = runtime;
        runtime.lists.blockCacheInvalidator = this::invalidate;
    }

    private void invalidate() {
        cache.clear();
        modified = 1;
    }

    private static long key(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    private static int chunkX(long value) {
        return (int) value;
    }

    private static int chunkZ(long value) {
        return (int) (value >>> 32);
    }

    private static int compareChunk(long a, long b) {
        int x = chunkX(a) - chunkX(b);
        return x != 0 ? x : chunkZ(a) - chunkZ(b);
    }

    private static ColoredItem findStyle(ColoredList list, int id) {
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

    private Chunk findChunk(long value) {
        int low = 0;
        int high = cache.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int difference = compareChunk(value, cache.get(middle).key);
            if (difference == 0) {
                return cache.get(middle);
            }
            if (difference < 0) {
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return null;
    }

    private Chunk insertChunk(long value) {
        int index = 0;
        while (index < cache.size() && compareChunk(value, cache.get(index).key) > 0) {
            ++index;
        }
        Chunk chunk = new Chunk(value);
        cache.add(index, chunk);
        return chunk;
    }

    private Chunk findOrInsertChunk(long value) {
        Chunk found = findChunk(value);
        return found != null ? found : insertChunk(value);
    }

    private void clearChunks() {
        cache.clear();
    }

    private static void rebuildMesh(Chunk chunk) {
        int blocks = chunk.blocks.size();
        float[] vertices = new float[blocks * 8 * 3];
        int[] colors = new int[blocks * 8];
        short[] indices = new short[blocks * INDICES.length];
        int vertexCount = 0;
        int indexCount = 0;
        for (Block source : chunk.blocks) {
            int base = vertexCount;
            ColoredItem style = source.style;
            int red = Sse.cvttss2si(style.color[0] * 255.0f) & 0xFF;
            int green = Sse.cvttss2si(style.color[1] * 255.0f) & 0xFF;
            int blue = Sse.cvttss2si(style.color[2] * 255.0f) & 0xFF;
            int color = red | green << 8 | blue << 16 | 0xFF000000;
            for (int vertex = 0; vertex < 8; ++vertex) {
                vertices[vertexCount * 3] = VERTICES[vertex * 3] + (float) source.x;
                vertices[vertexCount * 3 + 1] = VERTICES[vertex * 3 + 1] + (float) source.y;
                vertices[vertexCount * 3 + 2] = VERTICES[vertex * 3 + 2] + (float) source.z;
                colors[vertexCount] = color;
                ++vertexCount;
            }
            for (int offset : INDICES) {
                indices[indexCount] = (short) (base + offset);
                ++indexCount;
            }
        }
        chunk.vertices = vertices;
        chunk.colors = colors;
        chunk.indices = indices;
        chunk.indexCount = indexCount;
    }

    private Chunk scanSection(int x, int section, int z, char[] data, Chunk chunk) {
        ColoredList list = runtime.lists.blockEsp;
        for (int item = 0; item < list.count; ++item) {
            ColoredItem style = list.data[item];
            short wanted = (short) style.id;
            for (int batch = 0; batch < 512; ++batch) {
                int mask = 0;
                for (int lane = 0; lane < 8; ++lane) {
                    short packed = (short) (((short) data[batch * 8 + lane]) >> 4);
                    if (packed == wanted) {
                        mask |= 1 << lane;
                    }
                }
                if (mask == 0) {
                    continue;
                }
                int y = ((batch >>> 5) + (section << 4)) & 0xFF;
                int localZ = (batch >>> 1) & 15;
                for (int lane = 0; lane < 8; ++lane) {
                    if ((mask & (1 << lane)) != 0) {
                        if (chunk == null) {
                            chunk = findOrInsertChunk(key(x, z));
                        }
                        int localX = (((batch & 1) << 3) + lane) & 0xFF;
                        chunk.blocks.add(new Block(localX, y, localZ, style));
                    }
                }
            }
        }
        return chunk;
    }

    private void loadChunk(World world, int x, int z) {
        WorldChunk object = world.getChunkAt(x, z);
        if (object == null) {
            return;
        }
        Chunk chunk = null;
        WorldChunkSection[] sections = object.sections;
        for (int section = 0; section != 16; ++section) {
            WorldChunkSection storage = sections[section];
            if (storage == null) {
                continue;
            }
            chunk = scanSection(x, section, z, storage.blockStates, chunk);
        }
        if (chunk != null) {
            rebuildMesh(chunk);
        }
    }

    void updateChunk(BlockChangeEvent event) {
        int action = event.kind;
        if (Integer.compareUnsigned(action, 2) < 0) {
            Chunk existing = findChunk(key(event.x, event.z));
            if (existing != null) {
                cache.remove(existing);
            }
            if (action != 1) {
                return;
            }
            loadChunk(event.world, event.x, event.z);
            return;
        }
        if (action != 2) {
            return;
        }
        ColoredItem style = findStyle(runtime.lists.blockEsp, event.id);
        int x = event.x >> 4;
        int z = event.z >> 4;
        long value = key(x, z);
        Chunk chunk = findChunk(value);
        int localX = (event.x - (x << 4)) & 0xFF;
        int localZ = (event.z - (z << 4)) & 0xFF;
        int y = event.y & 0xFF;
        if (chunk == null) {
            if (style == null) {
                return;
            }
            chunk = insertChunk(value);
        } else {
            List<Block> blocks = chunk.blocks;
            for (int i = 0; i < blocks.size(); ++i) {
                Block block = blocks.get(i);
                if (block.x != localX || block.y != y || block.z != localZ) {
                    continue;
                }
                if (style != null) {
                    block.style = style;
                } else {
                    blocks.remove(i);
                    if (blocks.isEmpty()) {
                        cache.remove(chunk);
                        return;
                    }
                }
                rebuildMesh(chunk);
                return;
            }
            if (style == null) {
                return;
            }
        }
        chunk.blocks.add(new Block(localX, y, localZ, style));
        rebuildMesh(chunk);
    }

    private void collectChunks(ClientWorld world) {
        ChunkSource source = world.chunkSource;
        if (!(source instanceof ClientChunkCache)) {
            return;
        }
        List<WorldChunk> list = ((ClientChunkCache) source).chunks;
        int count = list.size();
        if (count <= 0) {
            return;
        }
        int index = 0;
        do {
            WorldChunk chunk = list.get(index);
            int x = chunk.chunkX;
            int z = chunk.chunkZ;
            Chunk existing = findChunk(key(x, z));
            if (existing != null) {
                cache.remove(existing);
            }
            loadChunk(world, x, z);
            ++index;
        } while (index != count);
    }

    void tick(ClientGameContext c) {
        ListSetting setting = allowedBlocks;
        if (c.focused == 1 && setting.modified()) {
            for (Chunk chunk : cache) {
                rebuildMesh(chunk);
            }
            setting.clearModified();
        }
        if (c.world == null) {
            clearChunks();
            return;
        }
        if (modified == 1) {
            clearChunks();
            collectChunks(c.world);
            modified = 0;
        }
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.module != descriptor) {
            return;
        }
        if (event.state == 0) {
            clearChunks();
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        if (c.world != null) {
            collectChunks(c.world);
        }
    }

    private static long nativeUnsigned(float value) {
        long first = Sse.cvttss2si64(value);
        long second = Sse.cvttss2si64(value - 0x1p63f);
        return first | (second & (first >> 63));
    }

    void render(WorldRenderEvent event) {
        if (cache.isEmpty()) {
            return;
        }
        DrawList draw = event.draw;
        draw.lineWidth(lineWidth.get());
        long requested = nativeUnsigned(blockRenderLimit.get() * 24.0f);
        long limit = shouldLimitBlockRender.get() ? requested : 0x1000;
        Vec3 camera = event.camera;
        double cameraX = camera.x * 0.0625;
        double cameraZ = 0.0625 * camera.z;
        float range = rangeSetting.get();
        for (Chunk chunk : cache) {
            double dx = cameraX - ((double) chunkX(chunk.key) + 0.5);
            double dz = cameraZ - ((double) chunkZ(chunk.key) + 0.5);
            if (!(CrtMath.hypot(dx, dz) > (double) range)) {
                draw.push();
                Vec3 origin = new Vec3(chunkX(chunk.key) << 4, 0.0, chunkZ(chunk.key) << 4);
                Vec3 point = origin.subtract(event.camera);
                draw.translate((float) point.x, (float) point.y, (float) point.z);
                long amount = Long.compareUnsigned(limit, chunk.indexCount) < 0 ? limit : chunk.indexCount;
                draw.lines(chunk.vertices, chunk.colors, chunk.indices, (int) amount);
                draw.pop();
                range = rangeSetting.get();
            }
        }
    }

    void clear() {
        ColoredList list = runtime.lists.blockEsp;
        list.count = 0;
        list.data = null;
        clearChunks();
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> tick((ClientGameContext) e.context));
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.WORLD_RENDER, e -> render(e), -200);
        on(EventTypes.BLOCK_CHANGE, e -> updateChunk(e));
        on(EventTypes.UNLOAD, e -> clear());
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(rangeSetting);
        w.show(allowedBlocks);
        w.heading("Block render limit");
        w.show(shouldLimitBlockRender);
        if (shouldLimitBlockRender.get())
            w.show(blockRenderLimit);
        w.heading("Customization");
        w.show(lineWidth);
    }
}
