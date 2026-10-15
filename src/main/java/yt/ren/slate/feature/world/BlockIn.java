package yt.ren.slate.feature.world;

import net.minecraft.block.Block;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import yt.ren.slate.aim.AimFrameDriver;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.aim.rotation.RotationApply;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.aim.rotation.SharedRotation;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.RotationRequestEvent;
import yt.ren.slate.feature.player.ItemUse;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

public class BlockIn extends Module {
    private final ScalarSetting speedSetting = register("speed", new ScalarSetting("Speed", "%.0f%%", 0.0f, 100.0f, 0.3f, 1.0f, 0.5f, 0.0f));
    private final BooleanSetting requireGround = register("require_ground", new BooleanSetting("On ground", 0, 0));

    private static final int[][] OVERHEAD_PROBES = {
            {0, 3, 0}, {0, 3, -1}, {1, 3, 0}, {0, 3, 1}, {-1, 3, 0},
            {-1, 3, -1}, {1, 3, -1}, {1, 3, 1}, {-1, 3, 1}, {0, 4, 0},
    };
    private static final int NORTH = 0;
    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 3;
    private static final int TOP = 4;
    private static final int[] ROOF_SHAPES = {NORTH, EAST, SOUTH, WEST, NORTH, EAST, SOUTH, WEST, TOP};
    private static final int[][] SIDE_DIRECTIONS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private static final byte[] ITEM_PRIORITIES = {
            2, 2, 2, 3, 3, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 3, -1, -1, 2,
            -1, -1, -1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, 2, -1, -1, -1, 4, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            3,
    };

    private final ClientRuntime runtime;
    private final AimRandom random;

    private long placementMask;
    private int startingDirection;
    private int rotationRequested;
    private int movementMode;
    private Rotation centerRotation = new Rotation(0.0f, 0.0f);
    private double previousY;
    private final TimedNoise overheadFirst = new TimedNoise();
    private final TimedNoise overheadSecond = new TimedNoise();
    private final TimedNoise sideX = new TimedNoise();
    private final TimedNoise sideZ = new TimedNoise();
    private final TimedNoise yawLimit = new TimedNoise();
    private final TimedNoise pitchLimit = new TimedNoise();

    public BlockIn(ClientRuntime runtime) {
        super(ModuleId.BLOCK_IN, "Block In", "Places blocks around you.", Category.BLOCK);
        traits(Trait.NO_TOGGLE, Trait.HIDDEN);
        this.runtime = runtime;
        this.random = runtime.random;
    }

    private float sample(TimedNoise state, float low, float high, double interval) {
        return state.sample(random, runtime.frameTime[0], low, high, interval);
    }

    static int itemPriority(int item) {
        int index = item - 1;
        if (Integer.compareUnsigned(index, ITEM_PRIORITIES.length) < 0) {
            return ITEM_PRIORITIES[index];
        }
        if (item == 159) {
            return 2;
        }
        if (item == 162) {
            return 3;
        }
        return -1;
    }

    private static boolean replaceable(int id) {
        return Integer.compareUnsigned(id, 32) < 0 && ((0x80000301 >>> id) & 1) != 0;
    }

    private static boolean empty(ClientWorld world, int x, int y, int z) {
        return replaceable(Block.getId(world.getBlockState(new BlockPos(x, y, z)).getBlock()));
    }

    private static boolean centered(Vec3 position) {
        double x = Math.abs(Geometry.remainderDouble(position.x, 1.0));
        double z = Math.abs(Geometry.remainderDouble(position.z, 1.0));
        return x <= 0.69 && x >= 0.31 && z >= 0.31 && z <= 0.69;
    }

    private Vec3 overheadPoint(int[] origin, int shape) {
        if (shape == NORTH || shape == SOUTH) {
            double x = shape == SOUTH ? origin[0] + 1 : origin[0];
            float baseY = (float) origin[1];
            double y = sample(overheadFirst, 3.6f, 3.8f, 0.5) + baseY;
            float baseZ = (float) origin[2];
            float zNoise = shape == SOUTH ? sample(overheadSecond, 1.1f, 1.2f, 0.5)
                    : sample(overheadSecond, -0.1f, -0.2f, 0.5);
            return new Vec3(x, y, zNoise + baseZ);
        }
        if (shape == WEST || shape == EAST) {
            float baseX = (float) origin[0];
            float baseY = (float) origin[1];
            float xNoise = shape == WEST ? sample(overheadFirst, -0.1f, -0.2f, 0.5)
                    : sample(overheadFirst, 1.1f, 1.2f, 0.5);
            float yNoise = sample(overheadSecond, 3.6f, 3.8f, 0.5);
            return new Vec3(xNoise + baseX, yNoise + baseY,
                    shape == WEST ? origin[2] + 1 : origin[2]);
        }
        float baseX = (float) origin[0];
        double x = sample(overheadFirst, 0.4f, 0.6f, 0.5) + baseX;
        double y = origin[1] + 4;
        float baseZ = (float) origin[2];
        double z = sample(overheadSecond, 0.4f, 0.6f, 0.5) + baseZ;
        return new Vec3(x, y, z);
    }

    private static class Placement {
        final Vec3 point;
        final int bit;

        Placement(Vec3 point, int bit) {
            this.point = point;
            this.bit = bit;
        }
    }

    private Placement selectPlacement(ClientGameContext c, int[] origin) {
        ClientWorld world = c.world;
        int probe = 0;
        do {
            if ((placementMask & (1L << (probe + 32))) != 0) {
                break;
            }
            int[] offset = OVERHEAD_PROBES[probe];
            if (!empty(world, origin[0] + offset[0], origin[1] + offset[1], origin[2] + offset[2])) {
                break;
            }
            ++probe;
        } while (probe != 10);
        if (probe != 0 && probe != 10) {
            int mark = 32 + (probe >= 5 && probe <= 8 ? probe - 4 : 0);
            return new Placement(overheadPoint(origin, ROOF_SHAPES[probe - 1]), mark);
        }
        int below = origin[1] - 1;
        int middle = origin[1] + 1;
        int upper = origin[1] + 2;
        int mask = (int) placementMask;
        for (int step = 0; step != 4; ++step) {
            int direction = (startingDirection + step) & 3;
            int[] offset = SIDE_DIRECTIONS[direction];
            Bounds playerBounds = AttackPrediction.bounds(c.player.getShape());
            int x = origin[0] + offset[0];
            int z = origin[2] + offset[1];
            Vec3 columnBase = new Vec3(x, origin[1], z);
            Bounds column = new Bounds(columnBase, columnBase.add(new Vec3(1.0, 3.0, 1.0)));
            if (Geometry.overlaps(playerBounds, column)) {
                continue;
            }
            int firstBit = direction * 3;
            int level;
            if ((mask & (1 << firstBit)) == 0 && empty(world, x, origin[1], z) && !empty(world, x, below, z)) {
                level = 0;
            } else if ((mask & (1 << (firstBit + 1))) == 0 && empty(world, x, middle, z)
                    && !empty(world, x, origin[1], z)) {
                level = 1;
            } else if ((mask & (1 << (firstBit + 2))) == 0 && empty(world, x, upper, z)
                    && !empty(world, x, middle, z)) {
                level = 2;
            } else {
                continue;
            }
            float low = level == 0 ? -0.25f : 0.25f;
            float high = level == 0 ? -0.4f : 0.4f;
            float xNoise = sample(sideX, low, high, 0.5);
            double shiftX = 0.5 - (double) (xNoise * (float) offset[0]);
            float zNoise = sample(sideZ, low, high, 0.5);
            double shiftZ = 0.5 - (double) (zNoise * (float) offset[1]);
            int y = origin[1] + level;
            return new Placement(new Vec3(shiftX + (double) x, y, shiftZ + (double) z), firstBit + level);
        }
        return null;
    }

    private static KeyBinding[] movementKeys(GameOptions options) {
        KeyBinding[] keys = options.keyBindings;
        return new KeyBinding[]{keys[2], keys[4], keys[3], keys[5]};
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.module != descriptor) {
            return;
        }
        ClientGameContext c = (ClientGameContext) event.context;
        if (event.state == 1) {
            placementMask = 0;
            Rotation rotation = RotationLifecycle.read(c.player);
            startingDirection = Sse.cvttss2si(RotationApply.roundRotationFloat(rotation.yaw / 90.0f)) % 4;
            return;
        }
        for (KeyBinding binding : movementKeys(c.client.options)) {
            binding.pressed = PhysicalKeys.pressed(binding);
        }
    }

    void filterRotation(RotationRequestEvent event) {
        if (rotationRequested != 1) {
            return;
        }
        event.enabled = 1;
        event.remap = 1;
        if (movementMode == 1) {
            event.angles = centerRotation;
        }
    }

    void updateKeys(ClientGameContext c) {
        if (movementMode == 0) {
            return;
        }
        KeyBinding[] keys = movementKeys(c.client.options);
        keys[0].pressed = movementMode == 1;
        keys[1].pressed = false;
        keys[2].pressed = false;
        keys[3].pressed = false;
    }

    private static Item heldItem(PlayerInventory inventory) {
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }

    private static float clamp(float lower, float upper, float value) {
        float top = Floats.min(upper, value);
        return value < lower ? lower : top;
    }

    void update(ClientGameContext c, Module descriptor) {
        rotationRequested = 0;
        movementMode = 0;
        if (c.focused != 1) {
            c.registry.queueModuleChange(descriptor, 0);
            return;
        }
        int grounded = c.player.onGround ? 1 : 0;
        if (((~(requireGround.get() ? 1 : 0) | grounded) & 1) == 0) {
            c.registry.queueModuleChange(descriptor, 0);
            return;
        }
        Vec3 position = new Vec3(c.player.x, c.player.y, c.player.z);
        double floorX = ScalarTrig.floorGameDouble(position.x);
        double baseY = ScalarTrig.floorGameDouble(position.y) + -1.0;
        double floorZ = ScalarTrig.floorGameDouble(position.z);
        if (Math.abs(position.y - previousY) > 0.01) {
            placementMask = 0;
        }
        previousY = position.y;
        int[] origin = {Sse.cvttsd2si(floorX), Sse.cvttsd2si(baseY), Sse.cvttsd2si(floorZ)};
        Placement selected = selectPlacement(c, origin);
        if (selected == null) {
            if ((grounded & 1) != 0 && centered(position)) {
                c.registry.queueModuleChange(descriptor, 0);
            }
            return;
        }
        SharedRotation rotation = runtime.sharedRotation;
        if (!AimFrameDriver.claimRotationPriority(rotation, 500)) {
            c.registry.queueModuleChange(descriptor, 0);
            return;
        }
        ItemUse.selectHotbarItem(c, runtime.itemUse, descriptor, 1, BlockIn::itemPriority);
        if (!ItemUse.itemIsNonAirBlock(heldItem(c.player.inventory))) {
            c.registry.queueModuleChange(descriptor, 0);
            return;
        }
        Rotation current = RotationLifecycle.read(c.player);
        Rotation effective = current.add(rotation.accumulator.delta);
        Vec3d eyeVector = c.player.getEyePosition(1.0f);
        Vec3 eye = new Vec3(eyeVector.x, eyeVector.y, eyeVector.z);
        Rotation requested = Geometry.directionBetween(eye, selected.point);
        Rotation raw = requested.subtract(effective);
        Rotation difference = new Rotation(Geometry.wrapYaw(raw.yaw), raw.pitch);
        float speed = speedSetting.get();
        float factor = (float) ((double) speed * 0.5 + 0.5);
        Rotation proportional = new Rotation(difference.yaw * factor, difference.pitch * factor);
        float yawNoise = sample(yawLimit, 0.8f, 1.2f, 0.2);
        float pitchNoise = sample(pitchLimit, 0.8f, 1.2f, 0.2);
        float yawBase = yawNoise * 180.0f;
        float laterSpeed = speedSetting.get();
        float yawBound = yawBase * laterSpeed;
        float pitchBound = 180.0f * pitchNoise * laterSpeed;
        Rotation limited = new Rotation(clamp(Floats.negate(yawBound), yawBound, difference.yaw),
                clamp(Floats.negate(pitchBound), pitchBound, difference.pitch));
        Rotation delta = new Rotation((limited.yaw - proportional.yaw) * 0.5f + proportional.yaw,
                (limited.pitch - proportional.pitch) * 0.5f + proportional.pitch);
        RotationLifecycle.addRotationDelta(rotation, delta);
        Rotation remaining = difference.subtract(delta);
        if (Floats.magnitude(remaining.pitch) + Floats.magnitude(remaining.yaw) < 1.0f) {
            KeyBinding use = c.client.options.keyBindings[1];
            use.clickCount = use.clickCount + 1;
            placementMask |= 1L << (selected.bit & 63);
        }
        rotationRequested = 1;
        if (centered(position)) {
            movementMode = 2;
            return;
        }
        Vec3 center = new Vec3(ScalarTrig.floorGameDouble(position.x) + 0.5, position.y,
                ScalarTrig.floorGameDouble(position.z) + 0.5);
        movementMode = 1;
        centerRotation = Geometry.directionBetween(position, center);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> {
        }, 0);
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.ROTATION_REQUEST, e -> filterRotation(e), -2000);
        on(EventTypes.UPDATE_PRE, e -> updateKeys((ClientGameContext) e.context), -1000);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context, this), -500);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(speedSetting);
        w.heading("Conditions");
        w.show(requireGround);
    }
}
