package yt.ren.slate.feature.combat;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.SettingLists.NamedItem;
import yt.ren.slate.ui.SettingsWidgets;

public class Weapons extends Module {
    private final BooleanSetting fist = register("fist", new BooleanSetting("Fist", 0, 0));
    private final BooleanSetting swords = register("swords", new BooleanSetting("Swords", 1, 0));
    private final BooleanSetting axes = register("axes", new BooleanSetting("Axes", 0, 0));
    private final ListSetting extraItems = register("extra_items", new ListSetting("Extra", "Search item", 0));
    private final BooleanSetting enchantSharpness = register("enchant_sharpness", new BooleanSetting("Sharpness", 0, 0));
    private final BooleanSetting enchantKnockback = register("enchant_knockback", new BooleanSetting("Knockback", 0, 0));
    private final BooleanSetting enchantFireAspect = register("enchant_fire_aspect", new BooleanSetting("Fire aspect", 0, 0));
    private final BooleanSetting slot1 = register("1", new BooleanSetting("Slot 1", 0, 0));
    private final BooleanSetting slot2 = register("2", new BooleanSetting("Slot 2", 0, 0));
    private final BooleanSetting slot3 = register("3", new BooleanSetting("Slot 3", 0, 0));
    private final BooleanSetting slot4 = register("4", new BooleanSetting("Slot 4", 0, 0));
    private final BooleanSetting slot5 = register("5", new BooleanSetting("Slot 5", 0, 0));
    private final BooleanSetting slot6 = register("6", new BooleanSetting("Slot 6", 0, 0));
    private final BooleanSetting slot7 = register("7", new BooleanSetting("Slot 7", 0, 0));
    private final BooleanSetting slot8 = register("8", new BooleanSetting("Slot 8", 0, 0));
    private final BooleanSetting slot9 = register("9", new BooleanSetting("Slot 9", 0, 0));

    private final ClientRuntime runtime;

    public Weapons(ClientRuntime runtime) {
        super(ModuleId.WEAPONS, "Weapons", "Configure which items are considered weapons.", Category.COMBAT);
        traits(Trait.NO_KEYBIND, Trait.NO_TOGGLE, Trait.HIDDEN);
        extraItems.attachStorage(runtime.lists.weaponsStorage());
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.UNLOAD, e -> runtime.lists.weapons.release());
    }

    private final BooleanSetting[] slots = {slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};
    private int cachedTick = Integer.MIN_VALUE;
    private boolean cachedAllowed;
    private int cachedTags;

    static int enchantmentTags(ItemStack stack) {
        NbtList list = stack.getEnchantments();
        if (list == null) {
            return 0;
        }
        int tags = 0;
        for (int index = 0; index < list.size(); index++) {
            short value = list.getCompound(index).getShort("id");
            if (value == 20) {
                tags |= 4;
            } else if (value == 19) {
                tags |= 2;
            } else if (value == 16) {
                tags |= 1;
            }
        }
        return tags;
    }

    private boolean enchanted(int tags) {
        return ((tags & 1) != 0 && enchantSharpness.get())
                || ((tags & 2) != 0 && enchantKnockback.get())
                || (Integer.compareUnsigned(tags, 4) >= 0 && enchantFireAspect.get());
    }

    private boolean extraItem(int id) {
        return runtime.lists.weapons.find(new NamedItem(null, id), (a, b) -> a.id - b.id) != null;
    }

    private boolean weaponType(Item item) {
        if (item == null) {
            return fist.get();
        }
        int id = Item.getId(item);
        int bit = id - 0x102;
        if (Integer.compareUnsigned(bit, 0x1d) < 0) {
            if (((0x2044600 >>> (bit & 0x1f)) & 1) != 0) {
                return swords.get();
            }
            if (((0x10222001 >>> (bit & 0x1f)) & 1) != 0) {
                return axes.get();
            }
        }
        return id != 0 ? extraItem(id) : fist.get();
    }

    public boolean allows(PlayerEntity player) {
        return allows(player, null);
    }

    public boolean allows(PlayerEntity player, int[] tagsOut) {
        if (player.ticks != cachedTick) {
            cachedTick = player.ticks;
            cachedTags = 0;
            PlayerInventory inventory = player.inventory;
            int slot = inventory.selectedSlot;
            ItemStack stack = inventory.getItem(slot);
            cachedAllowed = fist.get();
            if (stack != null) {
                cachedTags = enchantmentTags(stack);
                cachedAllowed = slots[slot].get() || enchanted(cachedTags) || weaponType(stack.getItem());
            }
        }
        if (tagsOut != null) {
            tagsOut[0] = cachedTags;
        }
        return cachedAllowed;
    }

    public int findSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 9; ++slot) {
            if (slots[slot].get()) {
                return slot;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && (enchanted(enchantmentTags(stack)) || weaponType(stack.getItem()))) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Item types");
        w.show(fist);
        w.show(swords);
        w.show(axes);
        w.show(extraItems);
        w.heading("Any item with");
        w.show(enchantSharpness);
        w.show(enchantKnockback);
        w.show(enchantFireAspect);
        w.heading("Any item in");
        w.show(slot1);
        w.show(slot2);
        w.show(slot3);
        w.show(slot4);
        w.show(slot5);
        w.show(slot6);
        w.show(slot7);
        w.show(slot8);
        w.show(slot9);
    }
}
