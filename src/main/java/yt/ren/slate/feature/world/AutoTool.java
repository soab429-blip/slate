package yt.ren.slate.feature.world;

import net.minecraft.block.Block;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.input.PhysicalKeys;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.setting.SettingLists.NamedItem;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;

public class AutoTool extends Module {
    private final ScalarSetting activationTime = register("activation_time", new ScalarSetting("Activation time", "%.0f ms", 1.0f, 50.0f, 0.0f, 10.0f, 3.0f, 0.0f));
    private final BooleanSetting switchBack = register("switch_back", new BooleanSetting("Switch back when done", 1, 0));
    private final BooleanSetting allowUsingSword = register("allow_using_sword", new BooleanSetting("Sword", 1, 0));
    private final BooleanSetting allowUsingTools = register("allow_using_tools", new BooleanSetting("Tool", 1, 0));
    private final BooleanSetting allowUsingFists = register("allow_using_fists", new BooleanSetting("Fists", 1, 0));
    private final BooleanSetting allowUsingOther = register("allow_using_other", new BooleanSetting("Other", 1, 0));
    private final BooleanSetting requireSneak = register("require_sneak", new BooleanSetting("Sneaking", 0, 0));
    private final BooleanSetting restrictAllowedBlocks = register("restrict_allowed_blocks", new BooleanSetting("Restrict allowed blocks", 0, 0));
    private final ListSetting allowedBlocks = register("allowed_blocks", new ListSetting("Allowed blocks", "Search item", 0));

    private final ClientRuntime runtime;
    private int restoreSlot = -1;
    private int selectedSlot = -1;
    private int countdown;

    public AutoTool(ClientRuntime runtime) {
        super(ModuleId.AUTO_TOOL, "Auto Tool", "Selects the best tool when digging.", Category.BLOCK);
        allowedBlocks.attachStorage(runtime.lists.autoToolStorage());
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.CONTROLLER, e -> update(e.game()));
        on(EventTypes.UNLOAD, e -> runtime.lists.autoTool.release());
    }

    private BooleanSetting allowanceFor(Item item) {
        if (item == null) {
            return allowUsingFists;
        }
        switch (Item.getId(item)) {
            case 0x100:
            case 0x101:
            case 0x102:
            case 0x10d:
            case 0x10e:
            case 0x10f:
            case 0x111:
            case 0x112:
            case 0x113:
            case 0x115:
            case 0x116:
            case 0x117:
            case 0x11c:
            case 0x11d:
            case 0x11e:
            case 0x167:
                return allowUsingTools;
            case 0x10b:
            case 0x10c:
            case 0x110:
            case 0x114:
            case 0x11b:
                return allowUsingSword;
            default:
                return allowUsingOther;
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

    private Block miningTarget(ClientGameContext c) {
        GameOptions options = c.client.options;
        if (c.player.hasItemInUse() || !PhysicalKeys.pressed(options.keyBindings[0])) {
            return null;
        }
        if (requireSneak.get() && !options.keyBindings[7].pressed) {
            return null;
        }
        HitResult hit = c.client.crosshairTarget;
        if (hit == null || hit.type.ordinal() != 1) {
            return null;
        }
        BlockPos position = hit.getPos();
        Block block = c.world.getBlockState(new BlockPos(position.getX(), position.getY(), position.getZ())).getBlock();
        if (block == null) {
            return null;
        }
        if (restrictAllowedBlocks.get()
                && runtime.lists.autoTool.find(new NamedItem(null, Block.getId(block)), (a, b) -> a.id - b.id) == null) {
            return null;
        }
        return block;
    }

    private void release(PlayerInventory inventory) {
        ModuleRegistry.releaseOwner(runtime.owner, this);
        if (restoreSlot != -1) {
            inventory.selectedSlot = restoreSlot;
            restoreSlot = -1;
        }
    }

    private void update(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        PlayerInventory inventory = c.player.inventory;
        int selected = inventory.selectedSlot;
        if (restoreSlot != -1 && selectedSlot != selected) {
            restoreSlot = -1;
            release(inventory);
            return;
        }
        Block block = restoreSlot != -1 || allowanceFor(heldItem(inventory)).get() ? miningTarget(c) : null;
        if (block == null) {
            countdown = Sse.cvttss2si(activationTime.get());
            release(inventory);
            return;
        }
        if (countdown > 0) {
            --countdown;
            release(inventory);
            return;
        }
        float best = 1.0f;
        int chosen = selected;
        for (int offset = 0; offset < 9; ++offset) {
            int candidate = (selected + offset) % 9;
            ItemStack stack = inventory.getItem(candidate);
            if (stack == null) {
                continue;
            }
            float speed = stack.getMiningSpeed(block);
            if (speed > best) {
                chosen = candidate;
                best = speed;
            }
        }
        if (chosen == selected) {
            return;
        }
        if (ModuleRegistry.claimOwner(runtime.owner, this)) {
            inventory.selectedSlot = chosen;
            selectedSlot = chosen;
            if (restoreSlot == -1 && switchBack.get()) {
                restoreSlot = selected;
            }
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(activationTime);
        w.show(switchBack);
        w.heading("Allow while using");
        w.show(allowUsingSword);
        w.show(allowUsingTools);
        w.show(allowUsingFists);
        w.show(allowUsingOther);
        w.heading("Conditions");
        w.show(requireSneak);
        w.show(restrictAllowedBlocks);
        if (restrictAllowedBlocks.get()) {
            w.show(allowedBlocks);
        }
    }
}
