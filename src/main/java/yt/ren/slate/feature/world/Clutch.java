package yt.ren.slate.feature.world;

import net.minecraft.block.Block;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import yt.ren.slate.aim.AimFrameDriver;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.random.TimedNoise;
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
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.CrtMath;
import yt.ren.slate.util.math.Floats;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.MathUtil;
import yt.ren.slate.util.math.ScalarTrig;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.math.Vec3;
import yt.ren.slate.util.structs.Bounds;
import yt.ren.slate.util.structs.Rotation;

public class Clutch extends Module {
    private final ScalarSetting range = register("range", new ScalarSetting("Range", "%.0f block(s)", 1.0f, 0.0f, 1.0f, 7.0f, 7.0f, 0.0f));
    private final ScalarSetting maxAimDist = register("max_aim_dist", new ScalarSetting("FOV", "%.0f\u00b0", 0.0f, 0.0f, 50.0f, 360.0f, 360.0f, 0.0f));
    private final ScalarSetting minHeight = register("min_height", new ScalarSetting("Minimum height", "%.0f block(s)", 1.0f, 0.0f, 1.0f, 10.0f, 4.0f, 0.0f));
    private final ScalarSetting clickSpeed = register("click_speed", new ScalarSetting("Click speed", "%.1f CPS", 0.1f, 0.0f, 10.0f, 20.0f, 18.0f, 0.0f));
    private final ScalarSetting randomization = register("randomization", new ScalarSetting("Randomization", "%.0f%%", 0.01f, 100.0f, 0.0f, 1.0f, 0.5f, 0.0f));
    private final ChoiceSetting selectBlocks = register("select_blocks", new ChoiceSetting("Select blocks", 3, 0, new String[]{"No", "On depletion", "Always"}, 2, 0, 0));
    private final BooleanSetting sidewaysOnly = register("sideways_only", new BooleanSetting("Only place sideways", 0, 0));
    private final ScalarSetting aimSpeed = register("aim_speed", new ScalarSetting("Base speed", "%.0f \u00b0/s", 1.0f, 20.0f, 20.0f, 180.0f, 30.0f, 0.0f));
    private final ScalarSetting aimMult = register("aim_mult", new ScalarSetting("Acceleration", "%.3fx", 0.0f, 0.0f, 0.3f, 1.0f, 0.5f, 0.0f));
    private final ScalarSetting aimMultStrength = register("aim_mult_strength", new ScalarSetting("Acceleration strength", "%.0f%%", 0.0f, 100.0f, 0.0f, 1.0f, 0.3f, 0.0f));
    private final BooleanSetting aimMultipoint = register("aim_multipoint", new BooleanSetting("Multipoint", 0, 0));
    private final ScalarSetting snapbackDelaySetting = register("snapback_delay", new ScalarSetting("Delay", "%.0f ms", 1.0f, 50.0f, 1.0f, 10.0f, 6.0f, 0.0f));
    private final ScalarSetting snapbackDuration = register("snapback_duration", new ScalarSetting("Duration", "%.0f ms", 1.0f, 50.0f, 1.0f, 10.0f, 4.0f, 0.0f));
    private final BooleanSetting snapbackInstantOnJump = register("snapback_instant_on_jump", new BooleanSetting("Keep jump direction", 0, 0));
    private final BooleanSetting snapbackDisableAfter = register("snapback_disable_after", new BooleanSetting("Disable afterwards", 0, 0));
    private final BooleanSetting requireAir = register("require_air", new BooleanSetting("In air", 1, 0));
    private final BooleanSetting requireDamage = register("require_damage", new BooleanSetting("Damaged", 0, 0));
    private final BooleanSetting requireBackwards = register("require_backwards", new BooleanSetting("Moving backwards", 0, 0));

    private static final int[] CANDIDATE_COUNTS = {1, 6, 19, 43, 79, 123, 163, 167};
    private static final int[] CANDIDATE_OFFSETS = {
            0x888, 0x887, 0x878, 0x788, 0x988, 0x889, 0x886, 0x877, 0x787, 0x987, 0x868, 0x778, 0x978, 0x688, 0xa88, 0x879,
            0x789, 0x989, 0x88a, 0x885, 0x876, 0x786, 0x986, 0x867, 0x777, 0x977, 0x687, 0xa87, 0x768, 0x968, 0x678, 0xa78,
            0x588, 0xb88, 0x869, 0x779, 0x979, 0x689, 0xa89, 0x87a, 0x78a, 0x98a, 0x88b, 0x884, 0x875, 0x785, 0x985, 0x866,
            0x776, 0x976, 0x686, 0xa86, 0x767, 0x967, 0x677, 0xa77, 0x587, 0xb87, 0x668, 0xa68, 0x578, 0xb78, 0x488, 0xc88,
            0x769, 0x969, 0x679, 0xa79, 0x589, 0xb89, 0x86a, 0x77a, 0x97a, 0x68a, 0xa8a, 0x87b, 0x78b, 0x98b, 0x88c, 0x874,
            0x784, 0x984, 0x865, 0x775, 0x975, 0x685, 0xa85, 0x766, 0x966, 0x676, 0xa76, 0x586, 0xb86, 0x667, 0xa67, 0x577,
            0xb77, 0x487, 0xc87, 0x568, 0xb68, 0x478, 0xc78, 0x669, 0xa69, 0x579, 0xb79, 0x489, 0xc89, 0x76a, 0x96a, 0x67a,
            0xa7a, 0x58a, 0xb8a, 0x86b, 0x77b, 0x97b, 0x68b, 0xa8b, 0x87c, 0x78c, 0x98c, 0x774, 0x974, 0x684, 0xa84, 0x765,
            0x965, 0x675, 0xa75, 0x585, 0xb85, 0x666, 0xa66, 0x576, 0xb76, 0x486, 0xc86, 0x567, 0xb67, 0x477, 0xc77, 0x569,
            0xb69, 0x479, 0xc79, 0x66a, 0xa6a, 0x57a, 0xb7a, 0x48a, 0xc8a, 0x76b, 0x96b, 0x67b, 0xa7b, 0x58b, 0xb8b, 0x77c,
            0x97c, 0x68c, 0xa8c, 0x575, 0xb75, 0x57b, 0xb7b,
    };
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final ClientRuntime runtime;
    private final AimRandom random;

    private float clickDelay;
    private int active;
    private int[] targetBlock = {0, 0, 0};
    private int verticalTarget;
    private int rotationRequested;
    private int ticksWithoutDamage;
    private int recentActive;
    private int snapbackDelay;
    private int disableAfter;
    private int snapbackRemaining;
    private Vec3 targetPoint = Vec3.ZERO;
    private final TimedNoise steepYaw = new TimedNoise();
    private final TimedNoise horizontalSpeed = new TimedNoise();
    private final TimedNoise verticalSpeed = new TimedNoise();
    private double jumpYawTotal;
    private final TimedNoise verticalX = new TimedNoise();
    private final TimedNoise verticalZ = new TimedNoise();
    private final TimedNoise pointMargin = new TimedNoise();
    private final TimedNoise pointX = new TimedNoise();
    private final TimedNoise pointZ = new TimedNoise();

    public Clutch(ClientRuntime runtime) {
        super(ModuleId.CLUTCH, "Clutch", "Clutches for you when falling.", Category.BLOCK);
        this.runtime = runtime;
        this.random = runtime.random;
    }



    private float sample(TimedNoise state, float low, float high, double interval) {
        return state.sample(random, runtime.frameTime[0], low, high, interval);
    }

    private static int integerMagnitude(int value) {
        int negative = -value;
        return negative < 0 ? value : negative;
    }

    private static int coordinateDistance(int a, int b) {
        return a < b ? b - a : a - b;
    }

    private static float clamp(float minimum, float maximum, float value) {
        float top = Floats.min(maximum, value);
        return value < minimum ? minimum : top;
    }

    private static boolean solid(ClientWorld world, int[] block) {
        return Block.getId(world.getBlockState(new BlockPos(block[0], block[1], block[2])).getBlock()) != 0;
    }

    private static int[] hitBlock(HitResult hit) {
        BlockPos position = hit.getPos();
        return new int[]{position.getX(), position.getY(), position.getZ()};
    }

    private static Vec3 adjacentBlock(HitResult hit) {
        int[] block = hitBlock(hit);
        int[] direction = DIRECTIONS[hit.face.ordinal()];
        return new Vec3((double) block[0] + direction[0], (double) block[1] + direction[1],
                (double) block[2] + direction[2]);
    }

    private static boolean sameBlock(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static class Scored {
        final float score;
        final Vec3 point;

        Scored(float score, Vec3 point) {
            this.score = score;
            this.point = point;
        }
    }

    Scored scorePoint(Vec3 eye, Rotation rotation, Vec3 reference, int[] block, int[] offset) {
        int blockX = block[0];
        int blockY = block[1];
        int blockZ = block[2];
        int offsetX = offset[0];
        int offsetY = offset[1];
        int offsetZ = offset[2];
        float marginScale = randomization.get();
        float marginNoise = sample(pointMargin, 0.0f, 0.05f, 1.0);
        int laterX = offset[0];
        int laterZ = offset[2];
        float xScale = randomization.get();
        float xNoise = sample(pointX, -0.01f, 0.01f, 0.5);
        float zScale = randomization.get();
        float zNoise = sample(pointZ, -0.01f, 0.01f, 0.5);
        double shiftX = xNoise * xScale;
        double shiftZ = zNoise * zScale;
        double referenceX = reference.x;
        if (!aimMultipoint.get()) {
            double fractionX = Math.abs(Geometry.remainderDouble(referenceX, 1.0));
            double referenceZ = reference.z;
            double fractionZ = Math.abs(Geometry.remainderDouble(referenceZ, 1.0));
            if (0.5 < fractionX) {
                fractionX = Math.abs(fractionX + -1.0);
            }
            if (0.5 < fractionZ) {
                fractionZ = Math.abs(fractionZ + -1.0);
            }

            shiftZ = shiftZ + fractionX * (targetPoint.z < referenceZ ? -1.0 : 1.0);
            shiftX = shiftX + fractionZ * (targetPoint.x < referenceX ? -1.0 : 1.0);
        }
        double margin = (double) (marginScale * marginNoise) + 0.1;
        double opposite = 1.0 - margin;
        double sideX = offsetX >>> 31;
        double sideZ = offsetZ >>> 31;
        double minimumX = margin;
        double maximumX = opposite;
        double minimumZ = sideZ;
        double maximumZ = sideZ;
        if (Integer.compareUnsigned(integerMagnitude(offsetX), integerMagnitude(offsetZ)) > 0) {
            minimumX = sideX;
            maximumX = sideX;
            minimumZ = margin;
            maximumZ = opposite;
        }
        int horizontalSteps = integerMagnitude(laterX) + integerMagnitude(laterZ);
        int targetX = offsetX + blockX;
        int targetY = offsetY + blockY;
        int targetZ = offsetZ + blockZ;
        float x = clamp((float) (minimumX + (double) targetX), (float) (maximumX + (double) targetX),
                (float) (shiftX + referenceX));
        double yBase = (double) targetY + 0.25;
        float y = clamp(0.0f, 0.65f, (float) ((double) horizontalSteps * 0.25));
        float z = clamp((float) (minimumZ + (double) targetZ), (float) (maximumZ + (double) targetZ),
                (float) (shiftZ + reference.z));
        Vec3 point = new Vec3(x, (double) y + yBase, z);
        Rotation requested = Geometry.directionBetween(eye, point);
        Rotation difference = requested.subtract(rotation);
        float pitch = Floats.magnitude(difference.pitch);
        float yaw = Geometry.wrapYaw(difference.yaw);
        return new Scored(Floats.magnitude(yaw) + pitch, point);
    }

    private boolean selectTarget(ClientGameContext c, Bounds bounds, Vec3 eye, Rotation rotation) {
        verticalTarget = 0;
        Vec3 reference = new Vec3((bounds.minimum.x + bounds.maximum.x) * 0.5, bounds.minimum.y + -0.99,
                (bounds.minimum.z + bounds.maximum.z) * 0.5);
        ClientWorld world = c.world;
        int[] origin = {Sse.cvttsd2si(ScalarTrig.floorGameDouble(reference.x)),
                Sse.cvttsd2si(ScalarTrig.floorGameDouble(reference.y)), Sse.cvttsd2si(ScalarTrig.floorGameDouble(reference.z))};
        if (minHeight.get() > 0.0f) {
            int counter = 1;
            int y = origin[1];
            float previous;
            do {
                if (solid(world, new int[]{origin[0], y, origin[2]})) {
                    return false;
                }
                previous = (float) counter;
                counter = counter + 1;
                y = y - 1;
            } while (minHeight.get() > previous);
        }
        int count = CANDIDATE_COUNTS[Sse.cvttss2si(range.get())];
        long retainedDistance = -3;
        float bestScore = Float.MAX_VALUE;
        Vec3 chosenPoint = null;
        int[] chosenBlock = null;
        if (!((double) targetBlock[1] > reference.y)) {
            int[] captured = targetBlock.clone();
            if (solid(world, captured)) {
                int[] offset = {targetBlock[0] - origin[0], targetBlock[1] - origin[1], targetBlock[2] - origin[2]};
                Scored scored = scorePoint(eye, rotation, reference, origin, offset);
                if (!(scored.score > maxAimDist.get())
                        && Geometry.distanceSquared(eye, scored.point) < 20.160100000000003) {
                    Vec3d from = new Vec3d(eye.x, eye.y, eye.z);
                    Vec3d to = new Vec3d(scored.point.x, scored.point.y, scored.point.z);
                    HitResult hit = world.rayTrace(from, to, false, true, false);
                    int[] accepted = null;
                    if (hit == null) {
                        accepted = targetBlock.clone();
                    } else {
                        int[] block = hitBlock(hit);
                        if (sameBlock(block, targetBlock)) {
                            accepted = block;
                        }
                    }
                    if (accepted != null) {
                        chosenPoint = scored.point;
                        chosenBlock = accepted;
                        if (scored.score != Float.MAX_VALUE) {
                            int distance = coordinateDistance(origin[0], accepted[0])
                                    + coordinateDistance(origin[1], accepted[1]) + coordinateDistance(origin[2], accepted[2]);
                            retainedDistance = Integer.toUnsignedLong(distance) - 2;
                            bestScore = scored.score;
                        }
                    }
                }
            }
        }
        int index = 0;
        do {
            int encoded = CANDIDATE_OFFSETS[index];
            int[] offset = {(encoded & 15) - 8, ((encoded >>> 4) & 15) - 8, ((encoded >>> 8) & 15) - 8};
            int[] block = {origin[0] + offset[0], origin[1] + offset[1], origin[2] + offset[2]};
            if (solid(world, block)) {
                if (offset[0] == 0 && offset[2] == 0) {
                    if (sidewaysOnly.get()) {
                        return false;
                    }
                    float marginScale = randomization.get();
                    float marginNoise = sample(pointMargin, 0.0f, 0.05f, 1.0);
                    double margin = (double) (marginNoise * marginScale) + 0.1;
                    float xScale = randomization.get();
                    float xNoise = sample(verticalX, -0.1f, 0.1f, 1.0);
                    double shiftX = xNoise * xScale;
                    float zScale = randomization.get();
                    float zNoise = sample(verticalZ, -0.1f, 0.1f, 1.0);
                    double shiftZ = zNoise * zScale;
                    float x = clamp((float) ((double) block[0] + margin), (float) ((double) (block[0] + 1) - margin),
                            (float) (reference.x + shiftX));
                    float z = clamp((float) ((double) block[2] + margin), (float) ((double) (block[2] + 1) - margin),
                            (float) (reference.z + shiftZ));
                    targetPoint = new Vec3(x, 1.0, z);
                    targetBlock = block;
                    verticalTarget = 1;
                    return true;
                }
                int distance = integerMagnitude(offset[0]) + integerMagnitude(offset[1]) + integerMagnitude(offset[2]);

                count = CANDIDATE_COUNTS[distance];
                Scored scored = scorePoint(eye, rotation, reference, origin, offset);
                if ((!(scored.score > bestScore) || Integer.toUnsignedLong(distance) < retainedDistance)
                        && Geometry.distanceSquared(eye, scored.point) < 20.160100000000003) {
                    chosenPoint = scored.point;
                    chosenBlock = block;
                    bestScore = scored.score;
                }
            }
            ++index;
        } while (index < count);
        if (bestScore > maxAimDist.get()) {
            return false;
        }
        if (chosenPoint == null) {
            return false;
        }
        targetPoint = chosenPoint;
        targetBlock = chosenBlock;
        return true;
    }

    void updateClicks(ClientGameContext c) {
        if (clickDelay > 0.0f) {
            clickDelay = clickDelay + -1.0f;
            if (clickDelay > 0.0f) {
                return;
            }
        }
        clickDelay = 20.0f / clickSpeed.get() + clickDelay;
        if (c.focused != 1 || active == 0) {
            return;
        }
        HitResult hit = c.client.crosshairTarget;
        if (hit == null || hit.type.ordinal() != 1) {
            return;
        }
        if (!sameBlock(hitBlock(hit), targetBlock)) {
            return;
        }
        if (verticalTarget == (hit.face.ordinal() != 1 ? 1 : 0)) {
            return;
        }
        KeyBinding use = c.client.options.keyBindings[1];
        use.clickCount = use.clickCount + 1;
        Vec3 next = adjacentBlock(hit);
        targetBlock = new int[]{Sse.cvttsd2si(next.x), Sse.cvttsd2si(next.y), Sse.cvttsd2si(next.z)};
    }

    void filterRotation(RotationRequestEvent event) {
        if (rotationRequested == 1) {
            event.enabled = 1;
            event.remap = 1;
        }
    }

    private static Item heldItem(PlayerInventory inventory) {
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }

    private boolean permitted(ClientGameContext c, int grounded) {
        LocalClientPlayerEntity player = c.player;
        if (((requireAir.get() ? 1 : 0) & grounded) != 0) {
            return false;
        }
        if (ticksWithoutDamage >= 11 && requireDamage.get()) {
            return false;
        }
        if (player.vehicle != null) {
            return false;
        }
        if (requireBackwards.get()) {
            double speed = CrtMath.hypot(player.velocityX, player.velocityZ);
            if (speed == 0.0) {
                return false;
            }
            Rotation rotation = RotationLifecycle.read(player);
            float radians = (float) ((double) rotation.yaw * -3.141592653589793 / 180.0 + -3.141592653589793);
            float sine = ScalarTrig.sine(radians);
            float cosine = ScalarTrig.cosine(radians);
            double projection = (double) sine * player.velocityX + (double) cosine * player.velocityZ;
            return CrtMath.acos(projection / speed) < 1.5707963267948966;
        }
        return true;
    }

    void update(ClientGameContext c, Module descriptor) {
        active = 0;
        rotationRequested = 0;
        if (c.focused != 1) {
            return;
        }
        LocalClientPlayerEntity player = c.player;
        RotationLifecycle lifecycle = runtime.rotation;
        SharedRotation shared = runtime.sharedRotation;
        int grounded = player.onGround ? 1 : 0;
        int damage = player.damagedTimer;
        ticksWithoutDamage = damage == 0 ? ticksWithoutDamage + 1 : 0;
        if (recentActive > 0) {
            --recentActive;
            if (grounded != 0 && lifecycle.displayRotationAvailable()
                    && snapbackInstantOnJump.get()) {
                KeyBinding jump = c.client.options.keyBindings[6];
                if (PhysicalKeys.pressed(jump)) {
                    Rotation current = RotationLifecycle.read(player);
                    Rotation effective = current.add(shared.accumulator.delta);
                    Rotation reference = lifecycle.requestedRotation();
                    double sign = jumpYawTotal < 0.0 ? 1.0 : -1.0;
                    float yaw = (float) ((double) random.range(5.0f, 15.0f) * sign);
                    float pitch = (float) ((double) random.range(5.0f, 15.0f) * sign);
                    jumpYawTotal = (double) yaw + jumpYawTotal;
                    float variation = randomization.get();
                    Rotation desired = reference.add(new Rotation(yaw * variation, pitch * variation));
                    Rotation raw = desired.subtract(effective);
                    RotationLifecycle.addRotationDelta(shared, new Rotation(Geometry.wrapYaw(raw.yaw), raw.pitch));
                    rotationRequested = 1;
                    snapbackDelay = Sse.cvttss2si(snapbackDuration.get());
                    return;
                }
            }
        }
        Bounds bounds = AttackPrediction.bounds(player.getShape());
        Vec3d eyeVector = player.getEyePosition(1.0f);
        Vec3 eye = new Vec3(eyeVector.x, eyeVector.y, eyeVector.z);
        Rotation current = RotationLifecycle.read(player);
        Rotation effective = current.add(shared.accumulator.delta);
        if (permitted(c, grounded) && selectTarget(c, bounds, eye, effective)) {
            int selection = selectBlocks.index();
            if (selection == 1 || selection == 2) {
                ItemUse.selectHotbarBlock(c, runtime.itemUse, descriptor, selection == 2 ? 1 : 0);
            }
            active = ItemUse.itemIsNonAirBlock(heldItem(player.inventory)) ? 1 : 0;
            if (active != 0) {
                float delay = snapbackDelaySetting.get();
                float maximum = (float) ((double) randomization.get() * 0.5 + 1.0);
                snapbackDelay = Sse.cvttss2si(random.range(1.0f, maximum) * delay);
                recentActive = 10;
                disableAfter = (snapbackDisableAfter.get() ? 1 : 0);
            }
        } else {
            active = 0;
        }
        if (active == 0) {
            if (snapbackDelay > 0) {
                --snapbackDelay;
                snapbackRemaining = Sse.cvttss2si(snapbackDuration.get());
            } else if (snapbackRemaining > 0) {
                --snapbackRemaining;
            } else {
                if (disableAfter == 1) {
                    c.registry.queueModuleChange(descriptor, 0);
                    disableAfter = 0;
                }
                return;
            }
        }
        if (!AimFrameDriver.claimRotationPriority(shared, 1000)) {
            return;
        }
        rotationRequested = 1;
        if (active == 0) {
            if (snapbackDelay > 0) {
                return;
            }
            int remaining = snapbackRemaining;
            if (remaining <= 0 || !lifecycle.displayRotationAvailable()) {
                return;
            }
            Rotation freshEffective = RotationLifecycle.read(player).add(shared.accumulator.delta);
            Rotation raw = lifecycle.requestedRotation().subtract(freshEffective);
            float scale = 1.0f / (float) remaining;
            RotationLifecycle.addRotationDelta(shared, new Rotation(Geometry.wrapYaw(raw.yaw) * scale, raw.pitch * scale));
            return;
        }
        int[] target = targetBlock;
        int top = target[1] + 1;
        Vec3 direction = MathUtil.lookDirection(RotationLifecycle.read(player));
        Vec3 segment = direction.scale(4.4);
        Bounds targetBounds = new Bounds(
                new Vec3((double) target[0] + 0.02, (double) target[1] + 0.02, (double) target[2] + 0.02),
                new Vec3((double) target[0] + 0.98, top, 0.98 + (double) target[2]));
        Vec3 intersection = Geometry.segmentBoundsIntersection(targetBounds, eye, segment);
        if (intersection != null && intersection.y < (double) targetBlock[1] + 0.99) {
            return;
        }
        Rotation desired = Geometry.directionBetween(eye, targetPoint);
        Rotation difference = desired.subtract(effective);
        float yaw;
        if (!((double) desired.pitch > 89.999)) {
            yaw = Geometry.wrapYaw(difference.yaw);
        } else {
            float variation = randomization.get();
            yaw = sample(steepYaw, -20.0f, 20.0f, 0.5) * variation;
        }
        float speed = aimSpeed.get();
        float horizontalMinimum = (float) ((double) randomization.get() * -0.25 + 1.0);
        float horizontal = horizontalSpeed.sampleStepped(random, horizontalMinimum, 1.0f, 10.0, 1.0) * speed;
        float verticalMinimum = (float) ((double) randomization.get() * -0.25 + 1.0);
        float vertical = speed * verticalSpeed.sampleStepped(random, verticalMinimum, 1.0f, 10.0, 1.0);
        float limitedYaw = clamp(Floats.negate(horizontal), horizontal, yaw);
        float limitedPitch = clamp(Floats.negate(vertical), vertical, difference.pitch);
        float horizontalBase = aimMult.get();
        float horizontalMultiplier = horizontalBase + horizontalSpeed.sampleStepped(random, -0.05f, 0.05f, 10.0, 1.0);
        float verticalBase = aimMult.get();
        float verticalMultiplier = verticalBase + verticalSpeed.sampleStepped(random, -0.05f, 0.05f, 10.0, 1.0);
        float strength = aimMultStrength.get();
        float deltaYaw = limitedYaw + (horizontalMultiplier * yaw - limitedYaw) * strength;
        float deltaPitch = limitedPitch + (verticalMultiplier * difference.pitch - limitedPitch)
                * aimMultStrength.get();
        RotationLifecycle.addRotationDelta(shared, new Rotation(deltaYaw, deltaPitch));
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updateClicks((ClientGameContext) e.context));
        on(EventTypes.ROTATION_REQUEST, e -> filterRotation(e), -100);
        on(EventTypes.UPDATE_POST, e -> update((ClientGameContext) e.context, this), -1000);
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(range);
        w.show(maxAimDist);
        w.show(minHeight);
        w.show(clickSpeed);
        w.show(randomization);
        w.show(selectBlocks);
        w.show(sidewaysOnly);
        w.heading("Aim");
        w.show(aimSpeed);
        w.show(aimMult);
        w.show(aimMultStrength);
        w.show(aimMultipoint);
        w.heading("Snap-back");
        w.show(snapbackDelaySetting);
        w.show(snapbackDuration);
        w.show(snapbackInstantOnJump);
        w.show(snapbackDisableAfter);
        w.heading("Conditions");
        w.show(requireAir);
        w.show(requireDamage);
        w.show(requireBackwards);
    }
}
