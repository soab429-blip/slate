package yt.ren.slate.feature.player;

import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ControllerEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Geometry;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.structs.Rotation;

public class InventoryManager extends Module {
    private final ScalarSetting delayAfterOpen = register("delay_after_open", new ScalarSetting("Delay after open", "%.0f ms", 0.0f, 50.0f, 0.0f, 10.0f, 3.0f, 0.0f));
    private final ScalarSetting speedSetting = register("speed", new ScalarSetting("Speed", "%.1fx", 0.0f, 0.0f, 1.0f, 4.0f, 2.0f, 0.0f));
    private final BooleanSetting smartSpeed = register("smart_speed", new BooleanSetting("Smart speed", 1, 0));
    private final BooleanSetting randomize = register("randomize", new BooleanSetting("Randomize", 1, 0));
    private final BooleanSetting equipArmor = register("equip_armor", new BooleanSetting("Enabled", 0, 0));
    private final BooleanSetting sortHotbar = register("sort_hotbar", new BooleanSetting("Enabled", 0, 0));
    private final BooleanSetting sortHotbarFallback = register("sort_hotbar_fallback", new BooleanSetting("Smart fallbacks", 1, 0));
    private final ChoiceSetting slot1 = register("1", new ChoiceSetting("Slot 1", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot2 = register("2", new ChoiceSetting("Slot 2", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot3 = register("3", new ChoiceSetting("Slot 3", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot4 = register("4", new ChoiceSetting("Slot 4", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot5 = register("5", new ChoiceSetting("Slot 5", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot6 = register("6", new ChoiceSetting("Slot 6", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot7 = register("7", new ChoiceSetting("Slot 7", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot8 = register("8", new ChoiceSetting("Slot 8", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting slot9 = register("9", new ChoiceSetting("Slot 9", 27, 0, new String[]{"None", "Axe", "Blocks", "Bow", "Chest", "Ender pearl", "Fireball", "Fishing rod", "Flint and steel", "Golden apple", "Healing potion", "Jump potion", "Ladders", "Lava bucket", "Pickaxe", "Shears", "Shovel", "Snowballs/eggs", "Soup", "Speed potion", "Stick", "Strength potion", "Sword", "TNT", "Water bucket", "Weapon", "Wool"}, 0, 0, 1));
    private final ChoiceSetting[] hotbarSlots = {slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};


    private static final int[] ARMOR_MATERIAL = {0, 0, 0, 0, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 1, 1, 1, 1};
    private static final int[] ARMOR_STRENGTH = {1, 3, 2, 1, 2, 5, 3, 1, 2, 5, 4, 1, 2, 6, 5, 2, 3, 8, 6, 3};
    private static final int[] MATERIAL_BONUS = {0, 0, 1, 2, 0, 3};
    private static final int[] COMBAT_WEIGHTS = weights(16, 50, 19, 1, 20, 1);
    private static final int[] BOW_WEIGHTS = weights(48, 100, 49, 10, 50, 1, 51, 1);
    private static final int[] TOOL_WEIGHTS = weights(16, 1, 32, 10, 62, 83);
    private static final int[] BUILDING_BLOCKS = {
            1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
            0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
            0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0,
            0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1,
    };

    private static final int[][] TOOL_CLASSES = toolClasses();

    private static int[] weights(int... pairs) {
        int[] table = new int[63];
        for (int i = 0; i < pairs.length; i += 2) {
            table[pairs[i]] = pairs[i + 1];
        }
        return table;
    }

    private static int[][] toolClasses() {
        int[][] table = new int[104][];
        for (int i = 0; i < table.length; ++i) {
            table[i] = new int[]{0, 0};
        }
        int[][] entries = {
                {0, 16, 3}, {1, 14, 3}, {2, 1, 3}, {11, 22, 3}, {12, 22, 1}, {13, 16, 1}, {14, 14, 1}, {15, 1, 1},
                {16, 22, 2}, {17, 16, 2}, {18, 14, 2}, {19, 1, 2}, {20, 22, 5}, {21, 16, 5}, {22, 14, 5}, {23, 1, 5},
                {27, 22, 4}, {28, 16, 4}, {29, 14, 4}, {30, 1, 4}, {103, 15, 1},
        };
        for (int[] entry : entries) {
            table[entry[0]] = new int[]{entry[1], entry[2]};
        }
        return table;
    }

    private final AimRandom random;

    private int phase;
    private long usedSlots;
    private int action;
    private int destination;
    private int resumePhase;
    private int categoryIndex;
    private int sourceSlot;
    private int advanceIndex;
    private float remaining;
    private Rotation previousPoint = new Rotation(0.0f, 0.0f);

    public InventoryManager(ClientRuntime runtime) {
        super(ModuleId.INV_MANAGER, "Inv Manager", "Organises your inventory for you.", Category.UTILITY);
        this.random = runtime.random;
    }

    static Rotation inventorySlotPoint(int slot) {
        if (Integer.compareUnsigned(slot, 9) < 0) {
            return new Rotation((float) Integer.toUnsignedLong(slot - 5), 1.0f);
        }
        return new Rotation((float) Integer.toUnsignedLong(Integer.divideUnsigned(slot, 9) + 3),
                (float) Integer.remainderUnsigned(slot, 9));
    }

    static int[] classifyTool(int item) {
        int index = item - 0x100;
        return Integer.compareUnsigned(index, TOOL_CLASSES.length) < 0 ? TOOL_CLASSES[index] : new int[]{0, 0};
    }

    static int scoreEnchantments(NbtList tags, int[] weights) {
        if (tags == null) {
            return 0;
        }
        int count = tags.size();
        int score = 0;
        for (int index = 0; index < count; ++index) {
            NbtCompound tag = tags.getCompound(index);
            int id = tag.getShort("id") & 0xFFFF;
            if (id >= weights.length || weights[id] == 0) {
                continue;
            }
            short level = tag.getShort("lvl");
            if (level >= 0) {
                score += weights[id] * level;
            }
        }
        return score;
    }

    static int scoreCategory(int category, int item, int damage, NbtList tags, int itemFlag) {
        if (Integer.compareUnsigned(category - 1, 25) > 0) {
            return 0;
        }
        switch (category) {
            case 2:
                return Integer.compareUnsigned(item - 1, BUILDING_BLOCKS.length) < 0 ? BUILDING_BLOCKS[item - 1] : 0;
            case 3:
                return item == 0x105 ? (scoreEnchantments(tags, BOW_WEIGHTS) + 1) & 0xFFFF : 0;
            case 4:
                return item == 0x36 ? 1 : 0;
            case 5:
                return item == 0x170 ? (2 - itemFlag) & 0xFFFF : 0;
            case 6:
                return item == 0x181 ? 1 : 0;
            case 7:
                return item == 0x15a ? 1 : 0;
            case 8:
                return item == 0x103 ? 1 : 0;
            case 9:
                return item == 0x142 ? 1 + (damage == 1 ? 1 : 0) : 0;
            case 10:
            case 11:
            case 19:
            case 21: {
                if (item != 0x175) {
                    return 0;
                }
                int base = 0;
                switch (damage & 15) {
                    case 1:
                        if (category == 10) {
                            base = 1;
                        }
                        break;
                    case 2:
                        if (category == 19) {
                            base = 1;
                        }
                        break;
                    case 5:
                        if (category == 10) {
                            base = 2;
                        }
                        break;
                    case 9:
                        if (category == 21) {
                            base = 1;
                        }
                        break;
                    case 11:
                        if (category == 11) {
                            base = 1;
                        }
                        break;
                    default:
                        break;
                }
                return base != 0 ? (base + ((damage >>> 6) & 1) + ((damage >>> 5) & 1)) & 0xFFFF : 0;
            }
            case 12:
                return item == 0x41 ? 1 : 0;
            case 13:
                return item == 0x147 ? 1 : 0;
            case 17:
                return item == 0x14c || item == 0x158 ? 1 : 0;
            case 18:
                return item == 0x19d || item == 0x11a ? 1 : 0;
            case 20:
                return item == 0x118 ? 1 : 0;
            case 22: {
                int[] tool = classifyTool(item);
                if (tool[0] != 22) {
                    return 0;
                }
                int base = MATERIAL_BONUS[tool[1]] * 40 + 160;
                return (scoreEnchantments(tags, COMBAT_WEIGHTS) + base) & 0xFFFF;
            }
            case 23:
                return item == 0x2e ? 1 : 0;
            case 24:
                return item == 0x146 ? 1 : 0;
            case 25: {
                int[] tool = classifyTool(item);
                int rank = 0;
                switch (tool[0]) {
                    case 1:
                        rank = 3;
                        break;
                    case 14:
                        rank = 2;
                        break;
                    case 16:
                        rank = 1;
                        break;
                    case 22:
                        rank = 4;
                        break;
                    default:
                        break;
                }
                int base = (rank + MATERIAL_BONUS[tool[1]]) * 40;
                return (scoreEnchantments(tags, COMBAT_WEIGHTS) + base) & 0xFFFF;
            }
            case 26:
                return item == 0x23 ? 1 : 0;
            default: {
                int[] tool = classifyTool(item);
                if (tool[0] != category) {
                    return 0;
                }
                return (scoreEnchantments(tags, TOOL_WEIGHTS) + tool[1] * 100) & 0xFFFF;
            }
        }
    }

    int scoreStack(ItemStack stack, int category) {
        Item item = stack.getItem();
        if (item == null) {
            return 0;
        }
        int id = Item.getId(item);
        int damage = stack.metadata;
        NbtList tags = stack.getEnchantments();
        int itemFlag = stack.hasCustomHoverName() ? 1 : 0;
        int score = scoreCategory(category, id, damage, tags, itemFlag);
        if (!sortHotbarFallback.get()) {
            return score;
        }
        if (score != 0) {
            return score << 16;
        }
        int fallback;
        switch (category) {
            case 22:
                fallback = 25;
                break;
            case 17:
                fallback = 7;
                break;
            case 7:
                fallback = 17;
                break;
            default:
                return 0;
        }
        return scoreCategory(fallback, id, damage, tags, itemFlag);
    }

    static int scoreArmor(ItemStack stack, int type) {
        if (stack == null) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == null) {
            return 0;
        }
        int index = Item.getId(item) - 0x12a;
        if (Integer.compareUnsigned(index, 20) >= 0 || Integer.remainderUnsigned(index, 4) != type) {
            return 0;
        }
        int material = ARMOR_MATERIAL[index];
        NbtList tags = stack.getEnchantments();
        int base = ARMOR_STRENGTH[material * 4 + type];
        double enchantment = 0.0;
        if (tags != null) {
            int count = tags.size();
            float other = 0.0f;
            float protection = 0.0f;
            for (int tagIndex = 0; tagIndex < count; ++tagIndex) {
                NbtCompound tag = tags.getCompound(tagIndex);
                int id = tag.getShort("id") & 0xFFFF;
                short level = tag.getShort("lvl");
                if (level < 0) {
                    continue;
                }
                if (Integer.compareUnsigned(id - 1, 4) < 0) {
                    other = (float) ((double) level * 0.1 + (double) other);
                } else if (id == 0) {
                    int squared = level * level + 6;
                    int q = (int) Sse.cvttsd2si64((double) squared * 0.75 / 3.0);
                    if (Integer.compareUnsigned(q, 25) >= 0) {
                        q = 25;
                    }
                    protection = (float) Integer.toUnsignedLong((q >>> 1) + ((q + 1) & 0x3e)) * 0.5f;
                }
            }
            enchantment = other + protection;
        }
        double unprotected = 1.0 - (double) base * 0.04;
        double enchanted = 1.0 - enchantment * 0.04;
        float reduced = (float) (1.0 - enchanted * unprotected);
        return (int) Sse.cvttss2si64(reduced * 1000.0f);
    }

    private void markUsed(int index) {
        int bit = 1 << (index & 31);
        usedSlots |= bit;
    }

    private void nextCategory(int value) {
        phase = value;
        usedSlots = 0;
        action = 0;
        destination = 0;
        resumePhase = 0;
        categoryIndex = 0;
        sourceSlot = 0;
        advanceIndex = 0;
    }

    private void addMovementDelay(Rotation point) {
        ScalarSetting speed = speedSetting;
        float delay = speed.maximum() / speed.get();
        float distance = Geometry.angleLength(point.subtract(previousPoint));
        if (smartSpeed.get()) {
            float scaled = distance * 0.5f;
            delay = delay * (scaled > 1.0f ? scaled : 1.0f);
        }
        if (randomize.get()) {
            delay = delay * random.range(1.0f, 1.25f);
        }
        remaining = delay + remaining;
        previousPoint = point;
    }

    private boolean selectArmor(PlayerInventory inventory) {
        int type = categoryIndex;
        ItemStack equipped = inventory.getItem(39 - type);
        ItemStack[] contents = inventory.items;
        int best = scoreArmor(equipped, type);
        int selected = -1;
        for (int slot = 0; slot < 36; ++slot) {
            int score = scoreArmor(contents[slot], type);
            if (Integer.compareUnsigned(best, score) < 0) {
                best = score;
                selected = slot;
            }
        }
        if (selected == -1) {
            return false;
        }
        if (equipped == null) {
            action = 0;
            destination = 0;
            sourceSlot = (selected < 9 ? selected + 36 : selected) & 0xFF;
        } else if (selected < 9) {
            action = 1;
            destination = selected & 0xFF;
            sourceSlot = (categoryIndex + 5) & 0xFF;
        } else {
            action = 2;
            destination = (categoryIndex + 5) & 0xFF;
            sourceSlot = selected & 0xFF;
        }
        return true;
    }

    private static long itemKey(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null) {
            return 0;
        }
        int id = Item.getId(item);
        int damage = stack.metadata;
        return ((long) id << 32) | ((long) damage & 0xFFFFFFFFL) | (damage < 0 ? 0xFFFFFFFF00000000L : 0);
    }

    private boolean selectHotbar(PlayerInventory inventory) {
        int index = categoryIndex;
        int category = hotbarSlots[index].index();
        if (category == 0) {
            return false;
        }
        ItemStack[] contents = inventory.items;
        ItemStack current = contents[index];
        int count = 0;
        int limit = 0;
        long key = 0;
        int rank = 0;
        if (current != null) {
            count = current.size;
            limit = current.getMaxSize();
            key = itemKey(current);
            rank = scoreStack(current, category);
        }
        ItemStack last = null;
        for (int slot = 9; slot < 36; ++slot) {
            last = contents[slot];
            if (last == null) {
                break;
            }
        }
        if (count <= 0) {
            count = 0;
        }
        int best = count * rank;
        int selected = index;
        boolean merge = false;
        for (int slot = 0; slot < 36; ++slot) {
            if (slot == index || ((usedSlots >>> slot) & 1) != 0) {
                continue;
            }
            ItemStack stack = contents[slot];
            if (stack == null) {
                continue;
            }
            int stackCount = stack.size;
            long candidateKey = itemKey(stack);
            int mergeScore = 0;
            if (count < limit && stackCount < limit && candidateKey == key && ItemStack.matchesNbt(current, stack)) {
                int combined = count + stackCount;
                if (combined >= limit) {
                    combined = limit;
                }
                mergeScore = combined * rank;
            }
            int product = scoreStack(stack, category) * stackCount;
            boolean useMerge = Integer.compareUnsigned(product - 1, mergeScore) < 0;
            int score = useMerge ? mergeScore : product;
            if (Integer.compareUnsigned(score, best) > 0 && (slot >= 9 || last == null || !useMerge)) {
                selected = slot;
                best = score;
                merge = useMerge;
            }
        }
        if (selected == index && best == 0) {
            return false;
        }
        if (selected != index) {
            action = merge ? 0 : 1;
            destination = merge ? 0 : index;
            sourceSlot = (Integer.compareUnsigned(selected, 9) < 0 ? selected + 36 : selected) & 0xFF;
        }
        markUsed(index);
        return selected != index;
    }

    private static void click(InventoryMenuScreen screen, int slot, int button, int mode) {
        screen.clickSlot(null, slot, button, mode);
    }

    void update(ControllerEvent event) {
        ClientGameContext c = event.game();
        InventoryMenuScreen screen = c.client.screen instanceof InventoryMenuScreen
                ? (InventoryMenuScreen) c.client.screen : null;
        if (screen == null || !(screen instanceof SurvivalInventoryScreen)) {
            phase = 0;
            usedSlots = 0;
            action = 0;
            destination = 0;
            resumePhase = 0;
            categoryIndex = 0;
            sourceSlot = 0;
            advanceIndex = 0;
            remaining = delayAfterOpen.get();
            if (randomize.get()) {
                remaining = random.range(1.0f, 1.5f) * remaining;
            }
            previousPoint = inventorySlotPoint(13);
            return;
        }
        int current = phase;
        if (current == 2) {
            return;
        }
        if (remaining > 0.0f) {
            remaining = remaining + -1.0f;
            if (remaining > 0.0f) {
                return;
            }
        }
        event.drain = 1;
        PlayerInventory inventory = c.player.inventory;
        while (true) {
            switch (current) {
                case 0:
                case 1: {
                    if ((current == 0 ? equipArmor : sortHotbar).get()) {
                        int count = current == 0 ? 4 : 9;
                        while (categoryIndex < count) {
                            if (current == 0 ? selectArmor(inventory) : selectHotbar(inventory)) {
                                addMovementDelay(inventorySlotPoint(sourceSlot));
                                resumePhase = current;
                                advanceIndex = current != 1 ? 1 : 0;
                                phase = 3;
                                return;
                            }
                            categoryIndex = (categoryIndex + 1) & 0xFF;
                        }
                    }
                    nextCategory(++current);
                    break;
                }
                case 2:
                    return;
                case 3: {
                    int act = action;
                    int target = destination & 0xFF;
                    if (Integer.compareUnsigned(act, 2) > 0) {
                        return;
                    }
                    click(screen, sourceSlot, act == 1 ? target : 0, act == 0 ? 1 : (act == 1 ? 2 : 0));
                    markUsed(categoryIndex);
                    if (advanceIndex == 1) {
                        categoryIndex = (categoryIndex + 1) & 0xFF;
                    }
                    if (act == 2) {
                        addMovementDelay(inventorySlotPoint(target));
                        sourceSlot = target;
                        phase = 4;
                        return;
                    }
                    current = resumePhase;
                    phase = current;
                    break;
                }
                case 4:
                    click(screen, sourceSlot, 0, 0);
                    addMovementDelay(new Rotation(previousPoint.yaw + -2.0f, previousPoint.pitch));
                    phase = 5;
                    return;
                case 5:
                    click(screen, -999, 0, 0);
                    current = resumePhase;
                    phase = current;
                    break;
                default:
                    return;
            }
        }
    }

    @Override
    protected void setup() {
        on(EventTypes.CONTROLLER, e -> update(e));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(delayAfterOpen);
        w.show(speedSetting);
        w.show(smartSpeed);
        w.show(randomize);
        w.heading("Equip armor");
        w.show(equipArmor);
        w.heading("Sort hotbar");
        w.show(sortHotbar);
        if (sortHotbar.get()) {
            w.show(sortHotbarFallback);
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
}
