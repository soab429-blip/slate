package yt.ren.slate.feature.block;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.feature.utility.ItemUse;
import yt.ren.slate.game.attack.AttackPrediction;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

public class BridgeAssist extends Module {
    private final ScalarSetting edgeOffsetSetting = register("edge_offset", new ScalarSetting("Edge offset", "%.2f blocks", 0.01f, 0.0f, 0.0f, 0.2999f, 0.2f, 0.0f));
    private final ScalarSetting unsneakDelaySetting = register("unsneak_delay", new ScalarSetting("Unsneak delay", "%.0f ms", 0.1f, 50.0f, 1.0f, 6.0f, 3.0f, 0.0f));
    private final ChoiceSetting selectBlocksSetting = register("select_blocks", new ChoiceSetting("Select blocks", 3, 0, new String[]{"No", "On depletion", "Always"}, 1, 0, 0));
    private final BooleanSetting randomize = register("randomize", new BooleanSetting("Randomize", 1, 0));
    private final BooleanSetting sneakOnJump = register("sneak_on_jump", new BooleanSetting("Sneak on jump", 0, 0));
    private final BooleanSetting sneakOnJumpRequireMoving = register("sneak_on_jump_require_moving", new BooleanSetting("Only jump-sneak while moving", 1, 0));
    private final BooleanSetting avoidDoubleSneak = register("avoid_double_sneak", new BooleanSetting("Avoid double-sneaking", 0, 0));
    private final BooleanSetting requireSneak = register("require_sneak", new BooleanSetting("Sneak key pressed", 0, 0));
    private final BooleanSetting requireHoldingBlocks = register("require_holding_blocks", new BooleanSetting("Holding blocks", 0, 0));
    private final BooleanSetting requireLookingDown = register("require_looking_down", new BooleanSetting("Looking down", 0, 0));
    private final BooleanSetting requireNotForward = register("require_not_forward", new BooleanSetting("Not moving forward", 0, 0));

    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final ClientRuntime runtime;
    private final AimRandom random;

    private int ownsSneak;
    private int placementTicks;
    private int filterPlacement;
    private int selectBlocks;
    private float unsneakRemaining;
    private int holdingGrace;
    private final TimedNoise edgeOffset = new TimedNoise();
    private final TimedNoise unsneakDelay = new TimedNoise();
    private final TimedNoise longDelayProbability = new TimedNoise();

    public BridgeAssist(ClientRuntime runtime) {
        super(ModuleId.BRIDGE_ASSIST, "Bridge Assist", "Sneaks and unsneaks for you while bridging.", Category.BLOCK);
        this.runtime = runtime;
        this.random = runtime.random;
    }


    private static boolean nonAir(ClientWorld world, int x, int y, int z) {
        return Block.getId(world.getBlockState(new BlockPos(x, y, z)).getBlock()) != 0;
    }

    private static Item heldItem(PlayerInventory inventory) {
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }

    boolean edgeAhead(GameOptions options, ClientWorld world, LocalClientPlayerEntity player) {
        Bounds box = AttackPrediction.bounds(player.getShape());
        Vec3 below = new Vec3((box.minimum.x + box.maximum.x) * 0.5, box.minimum.y - 0.01,
                (box.minimum.z + box.maximum.z) * 0.5);
        Vec3 velocity = new Vec3(player.velocityX, player.velocityY, player.velocityZ);
        double offset = edgeOffsetSetting.get();

        if (randomize.get()) {
            offset -= (double) edgeOffset.sample(random, runtime.frameTime[0], 0.0f, 0.15f, 5.0);
            offset = Math.max(offset, 0.0);
        }

        KeyBinding[] keys = options.keyBindings;

        float forwardAmount = 
            (keys[2].pressed ? 1.0f : 0.0f) -
            (keys[4].pressed ? 1.0f : 0.0f);

        float strafeAmount =
            (keys[3].pressed ? 1.0f : 0.0f) -
            (keys[5].pressed ? 1.0f : 0.0f);

        Rotation rotation = RotationLifecycle.read(player);
        float factor = player.speed;
        Vec3 acceleration = MathUtil.movementAcceleration(rotation, forwardAmount, strafeAmount, factor);
        Vec3 predicted = below.add(velocity.add(acceleration));
        int minX = Sse.cvttsd2si(ScalarTrig.floorGameDouble(predicted.x - offset));
        int minZ = Sse.cvttsd2si(ScalarTrig.floorGameDouble(predicted.z - offset));
        int y = Sse.cvttsd2si(ScalarTrig.floorGameDouble(predicted.y));
        double maxXFloor = ScalarTrig.floorGameDouble(predicted.x + offset);
        double maxZFloor = ScalarTrig.floorGameDouble(offset + predicted.z);
        if (nonAir(world, minX, y, minZ)) {
            return false;
        }
        int maxZ = Sse.cvttsd2si(maxZFloor);
        if (minZ != maxZ && nonAir(world, minX, y, maxZ)) {
            return false;
        }
        int maxX = Sse.cvttsd2si(maxXFloor);
        if (minX == maxX) {
            return true;
        }
        if (nonAir(world, maxX, y, minZ)) {
            return false;
        }
        return minZ == maxZ || !nonAir(world, maxX, y, maxZ);
    }

    void toggle(ToggleEvent event, Module descriptor) {
        if (event.module != descriptor || event.state != 0 || (ownsSneak & 1) == 0) {
            return;
        }

        ClientGameContext c = (ClientGameContext) event.context;
        KeyBinding sneak = c.client.options.keyBindings[7];
        sneak.pressed = PhysicalKeys.pressed(sneak);
        ownsSneak = 0;
    }

    void filterPlacement(ClickEvent event) {
        if (event.left != 0 || placementTicks == 20) {
            return;
        }

        ClientGameContext c = (ClientGameContext) event.context;
        if (filterPlacement == 1) {
            HitResult hit = c.client.crosshairTarget;
            if (hit != null && hit.type.ordinal() == 1 && Integer.compareUnsigned(hit.face.ordinal(), 2) >= 0
                    && c.player.onGround) {
                KeyBinding[] keys = c.client.options.keyBindings;

                if (keys[4].pressed && !keys[3].pressed && !keys[5].pressed) {
                    Rotation rotation = RotationLifecycle.read(c.player);
                    float angle = Math.abs((float) Geometry.remainderDouble(rotation.yaw, 90.0));

                    if (!(30.0f > angle) && !(angle > 60.0f)) {
                        Vec3 position = new Vec3(c.player.x, c.player.y, c.player.z);
                        Vec3 point = new Vec3(hit.facePos.x, hit.facePos.y, hit.facePos.z);
                        Vec3 difference = point.subtract(position);

                        if (!(Math.abs(difference.x) > 1.0) && !(-1.0 > difference.y) && !(difference.y > 0.0)
                                && !(Math.abs(difference.z) > 1.0)) {
                            BlockPos block = hit.getPos();
                            int[] direction = DIRECTIONS[hit.face.ordinal()];
                            Vec3 adjacent = new Vec3((double) block.getX() + direction[0],
                                    (double) block.getY() + direction[1], (double) block.getZ() + direction[2]);
                            boolean insideZ = adjacent.z < position.z && position.z < adjacent.z + 1.0;
                            double remainderX = Math.abs(Geometry.remainderDouble(point.x, 1.0));
                            double remainderZ = Math.abs(Geometry.remainderDouble(point.z, 1.0));
                            double margin = adjacent.x < position.x && position.x < adjacent.x + 1.0 && insideZ
                                    ? 0.25 : 0.15;
                            double upper = 1.0 - margin;
                            if ((!(upper < remainderX) && !(remainderX < margin))
                                    || (!(upper < remainderZ) && !(remainderZ < margin))) {
                                event.cancelled = 1;
                                return;
                            }
                        }
                    }
                }
            }
        }
        if (ItemUse.itemIsNonAirBlock(heldItem(c.player.inventory))) {
            placementTicks = 20;
        }
    }

    private void restore(KeyBinding sneak, boolean physical) {
        if (ownsSneak == 1) {
            sneak.pressed = physical;
            ownsSneak = 0;
        }
    }

    private void keep() {
        filterPlacement = (avoidDoubleSneak.get() ? 1 : 0);
    }

    private static boolean movementPressed() {
        KeyBinding[] keys = Minecraft.getInstance().options.keyBindings;
        for (int i = 2; i <= 5; ++i) {
            if (PhysicalKeys.pressed(keys[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean groundedOrRising(LocalClientPlayerEntity player) {
        if (player.onGround) {
            return true;
        }

        if (!sneakOnJump.get() || (sneakOnJumpRequireMoving.get() && !movementPressed())) {
            return false;
        }

        return !(player.velocityY < 0.0);
    }

    void update(ClientGameContext c) {
        filterPlacement = 0;
        selectBlocks = 0;
        if (c.focused != 1) {
            return;
        }
        GameOptions options = c.client.options;
        if (placementTicks > 0) {
            --placementTicks;
        }
        KeyBinding sneak = options.keyBindings[7];
        boolean physical = PhysicalKeys.pressed(sneak);
        if ((requireSneak.get() ? 1 : 0) != (physical ? 1 : 0)) {
            restore(sneak, physical);
            return;
        }

        LocalClientPlayerEntity player = c.player;

        if ((requireLookingDown.get() && 65.0f > RotationLifecycle.read(player).pitch) || (requireNotForward.get() && player.forwardSpeed > 0.0f)) {
            restore(sneak, physical);
            return;
        }

        selectBlocks = selectBlocksSetting.index() != 0 ? 1 : 0;

        if (requireHoldingBlocks.get()) {
            if (ItemUse.itemIsNonAirBlock(heldItem(player.inventory))) {
                holdingGrace = 2;
            } else {
                if (holdingGrace <= 0) {
                    restore(sneak, physical);
                    return;
                }
                --holdingGrace;
            }
        }
        ownsSneak = 1;
        boolean pressed = sneak.pressed;
        ClientWorld world = c.world;
        if (!pressed) {
            if (groundedOrRising(player) && edgeAhead(options, world, player)) {
                sneak.pressed = true;
                keep();
            }
            return;
        }
        boolean checkEdge = true;
        if (requireSneak.get()) {
            if (player.forwardSpeed >= 0.0f || placementTicks <= 0) {
                keep();
                return;
            }
        } else {
            checkEdge = groundedOrRising(player);
        }
        if (checkEdge && edgeAhead(options, world, player)) {
            keep();
            return;
        }
        unsneakRemaining -= 1.0f;
        if (unsneakRemaining > 0.0f) {
            keep();
            return;
        }
        sneak.pressed = false;
        float delay = unsneakDelaySetting.get();
        if (randomize.get()) {
            delay += unsneakDelay.sample(random, runtime.frameTime[0], -0.5f, 0.5f, 30.0);
            if (longDelayProbability.belowSample(random, runtime.frameTime[0], 0.04f, 0.08f, 60.0)) {
                delay += random.range(0.0f, 3.0f);
            }
        }
        unsneakRemaining += delay;
    }

    void selectBlocks(ClientGameContext c, Module descriptor) {
        if (selectBlocks == 1) {
            ItemUse.selectHotbarBlock(c, runtime.itemUse, descriptor,
                    selectBlocksSetting.index() == 2 ? 1 : 0);
        }
    }

    @Override
    protected void setup() {
        on(EventTypes.MODULE_TOGGLE, e -> toggle(e, this));
        on(EventTypes.CLICK_PRE, e -> filterPlacement(e));
        on(EventTypes.UPDATE_ROTATION, e -> update((ClientGameContext) e.context));
        on(EventTypes.UPDATE_POST, e -> selectBlocks((ClientGameContext) e.context, this));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(edgeOffsetSetting);
        w.show(unsneakDelaySetting);
        w.show(selectBlocksSetting);
        w.show(randomize);
        w.show(sneakOnJump);
        if (sneakOnJump.get()) {
            w.show(sneakOnJumpRequireMoving);
        }
        w.show(avoidDoubleSneak);
        w.heading("Conditions");
        w.show(requireSneak);
        w.show(requireHoldingBlocks);
        w.show(requireLookingDown);
        w.show(requireNotForward);
    }
}
