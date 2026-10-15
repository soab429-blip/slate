package yt.ren.slate.feature.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.event.Events.MoveEvent;
import yt.ren.slate.util.math.Vec3;

class Motion {
    static boolean stationary(MoveEvent e) {
        return e.forward == 0.0f && e.strafe == 0.0f;
    }

    static InventoryMenuScreen screen(Minecraft client) {
        Screen object = client.screen;
        return object instanceof InventoryMenuScreen ? (InventoryMenuScreen) object : null;
    }

    static KeyBinding key(GameOptions options, int index) {
        return options.keyBindings[index];
    }

    static Vec3 velocity(LocalClientPlayerEntity p) {
        return new Vec3(p.velocityX, p.velocityY, p.velocityZ);
    }

    static void writeVelocity(LocalClientPlayerEntity p, Vec3 v) {
        p.velocityX = v.x;
        p.velocityY = v.y;
        p.velocityZ = v.z;
    }

    static double horizontalSquared(Vec3 v) {
        return v.x * v.x + v.z * v.z;
    }

    static double minsd(double a, double b) {
        return a < b ? a : b;
    }

    static float maxss(float a, float b) {
        return a > b ? a : b;
    }

    static Item heldItem(LocalClientPlayerEntity player) {
        PlayerInventory inventory = player.inventory;
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }
}
