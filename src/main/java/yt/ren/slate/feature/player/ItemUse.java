package yt.ren.slate.feature.player;

import net.minecraft.block.Block;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleRegistry;

public class ItemUse {
    public static class State {
        public final Module[] owner;
        public Module selector;
        public int rememberedItem;

        public int age;
        public int advanceSlot;
        public int beforeItem;
        public int beforeCount;
        public int beforeSlot;

        public int remainingCount;

        public State(Module[] owner) {
            this.owner = owner;
        }
    }

    @FunctionalInterface
    public interface Priority {
        int score(int item);
    }

    public static boolean itemIsNonAirBlock(Item value) {
        if (value == null) {
            return false;
        }
        Block block = Block.byItem(value);
        return block != null && Block.getId(block) != 0;
    }

    public static void selectHotbarItem(ClientGameContext c, State s, Module module, int force,
                                        Priority priority) {
        if (s.selector != null || (force == 0 && s.rememberedItem == 0)) {
            return;
        }
        PlayerInventory inventory = c.player.inventory;
        int remembered = s.rememberedItem;
        int current = inventory.selectedSlot;
        int advance = s.advanceSlot;
        ItemStack[] contents = inventory.items;
        int selected = -1;
        int best = -1;
        for (int index = 0; index < 9; ++index) {
            int candidate = (current + advance + index) % 9;
            ItemStack stack = contents[candidate];
            if (stack == null) {
                continue;
            }
            Item value = stack.getItem();
            if (value == null) {
                continue;
            }
            int id = Item.getId(value);
            if (remembered != 0 && id == remembered) {
                selected = candidate;
                break;
            }
            int score = priority.score(id);
            if (score > best) {
                best = score;
                selected = candidate;
            }
        }
        if (selected != -1 && ModuleRegistry.claimOwner(s.owner, module)) {
            inventory.selectedSlot = selected;
            s.selector = module;
        }
    }

    public static int blockItemPriority(int item) {
        switch (item) {
            case 1:
            case 2:
            case 3:
            case 0x23:
            case 0x2d:
                return 2;
            case 5:
            case 0x11:
            case 0x14:
            case 0x18:
            case 0x57:
                return 1;
            default: {
                int offset = item - 0x79;
                return Integer.compareUnsigned(offset, 0x2a) < 0 && ((0x24000000001L >>> offset) & 1) != 0 ? 1 : -1;
            }
        }
    }

    public static void selectHotbarBlock(ClientGameContext c, State s, Module module, int force) {
        selectHotbarItem(c, s, module, force, ItemUse::blockItemPriority);
    }

    public static void finishHotbarFrame(State s) {
        if (s.selector != null) {
            ModuleRegistry.releaseOwner(s.owner, s.selector);
            s.selector = null;
        }
        ++s.age;
        s.rememberedItem = 0;
        s.advanceSlot = 0;
    }

    public static void beforeItemUse(ClientGameContext c, State s) {
        if (s.beforeItem != 0) {
            return;
        }
        PlayerInventory inventory = c.player.inventory;
        int current = inventory.selectedSlot;
        ItemStack stack = inventory.items[current];
        if (stack == null) {
            return;
        }
        Item value = stack.getItem();
        if (!itemIsNonAirBlock(value)) {
            return;
        }
        s.beforeItem = Item.getId(value);
        s.beforeCount = stack.size;
        if (Integer.compareUnsigned(s.age, 20) > 0 || s.beforeSlot != current) {
            s.remainingCount = s.beforeCount;
        }
        s.age = 0;
        s.beforeSlot = current;
    }

    public static void afterItemUse(ClientGameContext c, State s) {
        if (s.beforeItem == 0) {
            return;
        }
        PlayerInventory inventory = c.player.inventory;
        int current = inventory.selectedSlot;
        ItemStack stack = inventory.items[current];
        boolean exhausted = stack == null;
        if (stack != null) {
            Item value = stack.getItem();
            exhausted = value == null || Item.getId(value) == 0;
            if (!exhausted && stack.size < s.beforeCount) {
                exhausted = --s.remainingCount == 0;
            }
        }
        if (exhausted) {
            s.rememberedItem = s.beforeItem;
            s.advanceSlot = 1;
        }
        s.beforeItem = 0;
    }
}
