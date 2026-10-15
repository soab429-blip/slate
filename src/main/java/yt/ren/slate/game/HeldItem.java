package yt.ren.slate.game;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.feature.combat.Weapons;

public class HeldItem {
    public static boolean holdingWeapon(ClientRuntime runtime, PlayerEntity player) {
        return runtime.module(Weapons.class).allows(player);
    }

    public static Item selected(PlayerInventory inventory) {
        int slot = inventory.selectedSlot;
        if (Integer.compareUnsigned(slot, 9) >= 0) {
            return null;
        }
        ItemStack stack = inventory.items[slot];
        return stack != null ? stack.getItem() : null;
    }
}
