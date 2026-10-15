package yt.ren.slate.aim.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.structs.Rotation;

public class RotationLifecycle {
    public static final int CURRENT = 0;
    public static final int PREVIOUS = 1;
    public static final int FIELDS_8C0 = 2;
    public static final int FIELDS_8B0 = 3;

    public final SharedRotation shared;
    public float step;
    public int remapRequested;
    public int keysRemapped;
    public int restoreReference;
    public Rotation savedCurrent = new Rotation(0.0f, 0.0f);
    public Rotation savedPrevious = new Rotation(0.0f, 0.0f);
    public Rotation display = new Rotation(0.0f, 0.0f);
    public int displayChanged;
    public Rotation beforeInput = new Rotation(0.0f, 0.0f);
    public int inputRotationActive;
    public int savedKeys;

    public RotationLifecycle(SharedRotation shared) {
        this.shared = shared;
    }

    static float clampPitch(float value) {
        return value < -90.0f ? -90.0f : Math.min(90.0f, value);
    }

    public static Rotation read(Entity player, boolean previous) {
        return previous ? new Rotation(player.lastYaw, player.lastPitch) : new Rotation(player.yaw, player.pitch);
    }

    public static Rotation read(Entity player) {
        return read(player, false);
    }

    public static void write(LocalClientPlayerEntity player, Rotation value, int pair) {
        switch (pair) {
            case CURRENT:
                player.yaw = value.yaw;
                player.pitch = clampPitch(value.pitch);
                break;
            case PREVIOUS:
                player.lastYaw = value.yaw;
                player.lastPitch = clampPitch(value.pitch);
                break;
            case FIELDS_8C0:
                player.lastEasedYaw = value.yaw;
                player.lastEasedPitch = value.pitch;
                break;
            case FIELDS_8B0:
                player.easedYaw = value.yaw;
                player.easedPitch = value.pitch;
                break;
            default:
                break;
        }
    }

    public static void applyRotation(LocalClientPlayerEntity player, RotationAccumulator accumulator, float step,
                                     int writeSecondary) {
        if (step == 0.0f) {
            return;
        }
        float yaw = RotationApply.quantize(accumulator.delta.yaw, step);
        float remainderYaw = accumulator.delta.yaw - yaw;
        accumulator.delta = new Rotation(remainderYaw, accumulator.delta.pitch);
        float pitch = RotationApply.quantize(accumulator.delta.pitch, step);
        accumulator.delta = new Rotation(remainderYaw, accumulator.delta.pitch - pitch);
        if (yaw != 0.0f || pitch != 0.0f) {
            Rotation value = read(player).add(new Rotation(yaw, pitch));
            write(player, value, CURRENT);
            write(player, value, PREVIOUS);
            if (writeSecondary != 0) {
                write(player, value, FIELDS_8C0);
                write(player, value, FIELDS_8B0);
            }
        }
    }

    public static float roundRotationFloat(float value) {
        return RotationApply.roundRotationFloat(value);
    }

    public static void addRotationDelta(SharedRotation s, Rotation delta) {
        s.accumulator.delta = s.accumulator.delta.add(delta);
        s.accumulator.flag = 0;
    }

    public void beginRotationFrame(Minecraft game) {
        float sensitivity = game.options.mouseSensitivity;
        float factor = sensitivity * 0.6f + 0.2f;
        step = factor * factor * factor * 8.0f * 0.15f;
        int previous = shared.accumulator.flag;
        ++shared.accumulator.flag;
        if (previous > 0) {
            shared.accumulator.delta = new Rotation(0.0f, 0.0f);
        }
        shared.priority = 0;
    }

    public float retainRotationRemainder(float value) {
        float original = value;
        float negative = Float.intBitsToFloat(Float.floatToRawIntBits(step) ^ 0x80000000);
        float quantized = 0.0f;
        if (original <= negative || step <= original) {
            quantized = RotationApply.truncateRotationFloat(original / step) * step;
        }
        return original - quantized;
    }

    private static void press(KeyBinding binding, int value) {
        binding.pressed = value != 0;
    }

    public void remapMovementKeys(int remap) {
        Minecraft game = Minecraft.getInstance();
        if (!game.focused) {
            return;
        }
        KeyBinding[] keys = game.options.keyBindings;
        KeyBinding forward = keys[2];
        KeyBinding back = keys[4];
        KeyBinding left = keys[3];
        KeyBinding right = keys[5];
        if (remap == 0) {
            press(forward, savedKeys & 1);
            press(back, (savedKeys >> 1) & 1);
            press(left, (savedKeys >> 2) & 1);
            press(right, (savedKeys >> 3) & 1);
            return;
        }
        int f = forward.pressed ? 1 : 0;
        int b = back.pressed ? 1 : 0;
        int l = left.pressed ? 1 : 0;
        int r = right.pressed ? 1 : 0;
        savedKeys = (f | (b << 1) | (l << 2) | (r << 3)) & 0xFF;
        LocalClientPlayerEntity player = game.player;
        Rotation current = read(player);
        float difference = Geometry.wrapYaw(current.yaw - shared.reference.yaw);
        int front = (savedKeys & 1) - ((savedKeys >> 1) & 1);
        int side = ((savedKeys >> 2) & 1) - ((savedKeys >> 3) & 1);
        int direction = 0;
        if (front != 0 || side != 0) {
            int sector;
            if (front == 0) {
                sector = side == 1 ? 2 : (side == -1 ? 6 : 0);
            } else if (front == -1) {
                sector = 5 - ((side + 1) & 0xFF);
            } else {
                sector = side == 1 ? 1 : (side == -1 ? 7 : 0);
            }
            float rounded = roundRotationFloat(difference / 45.0f);
            int offset = Sse.cvttss2si(rounded);
            int[] directions = {0x0001, 0x0101, 0x0100, 0x01ff, 0x00ff, 0xffff, 0xff00, 0xff01};
            direction = directions[(offset + sector) & 7];
        }
        press(forward, (direction & 255) == 1 ? 1 : 0);
        press(back, (direction & 255) == 255 ? 1 : 0);
        press(left, (direction >> 8) == 1 ? 1 : 0);
        press(right, (direction >> 8) == 255 ? 1 : 0);
    }

    public void prepareRotationUpdate() {
        if (remapRequested == 1) {
            remapMovementKeys(1);
            keysRemapped = 1;
        }
    }

    public void finishRotationUpdate(ClientGameContext c, int enabled, int remap, int restore, Rotation requested) {
        if (keysRemapped == 1) {
            remapMovementKeys(0);
            keysRemapped = 0;
            remapRequested = 0;
        }
        if (enabled != 0) {
            if (!shared.referenceAvailable && c.player != null) {
                shared.referenceAvailable = true;
                display = read(c.player);
            }
            restoreReference = restore;
            shared.reference = requested;
            remapRequested = remap;
            return;
        }
        if (!shared.referenceAvailable) {
            return;
        }
        shared.referenceAvailable = false;
        Rotation target = restoreReference != 0 ? shared.reference : display;
        Rotation current = read(c.player);
        Rotation adjusted = new Rotation(Geometry.wrapYaw(target.yaw - current.yaw) + current.yaw, target.pitch);
        write(c.player, adjusted, CURRENT);
        write(c.player, adjusted, PREVIOUS);
        write(c.player, adjusted, FIELDS_8C0);
        write(c.player, adjusted, FIELDS_8B0);
    }

    public void swapDisplayRotation(LocalClientPlayerEntity player, int save) {
        if (save != 0) {
            savedCurrent = read(player);
            savedPrevious = read(player, true);
        }
        write(player, save != 0 ? display : savedCurrent, CURRENT);
        write(player, save != 0 ? display : savedPrevious, PREVIOUS);
    }

    public boolean displayRotationAvailable() {
        return shared.referenceAvailable;
    }

    public Rotation displayRotation() {
        return display;
    }

    public Rotation requestedRotation() {
        return shared.reference;
    }

    public void changeDisplayRotation(Rotation value) {
        float wrapped = Geometry.wrapYaw(value.yaw - display.yaw);
        displayChanged = 1;
        display = new Rotation(wrapped + display.yaw, value.pitch);
    }

    public void beginInputRotation() {
        LocalClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        beforeInput = read(player);
        if (displayChanged == 1) {
            displayChanged = 0;
            write(player, display, CURRENT);
            write(player, display, PREVIOUS);
        }
        if (shared.referenceAvailable) {
            inputRotationActive = 1;
            write(player, display, CURRENT);
        }
    }

    public void finishInputRotation(LocalClientPlayerEntity player) {
        if (inputRotationActive != 1) {
            return;
        }
        display = read(player);
        inputRotationActive = 0;
        write(player, beforeInput, CURRENT);
        write(player, beforeInput, PREVIOUS);
    }
}
