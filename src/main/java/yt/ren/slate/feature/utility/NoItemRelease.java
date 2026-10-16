package yt.ren.slate.feature.utility;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class NoItemRelease extends Module {
    private static final int SWORD_MODE = 0;
    private static final int CONSUMABLE_MODE = 1;

    private final ChoiceSetting mode = register("mode", new ChoiceSetting("Mode", 3, 0, new String[]{"Sword", "Consumable", "All"}, 0, 0, 0));

    private boolean suppressRelease;

    public NoItemRelease(ClientRuntime runtime) {
        super(ModuleId.NO_ITEM_RELEASE, "No Item Release", "Hides item release from server.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.PACKET_SEND, this::filter);
    }

    @Override
    public String suffix() {
        return mode.selected();
    }

    private static boolean isSword(int id) {
        int index = id - 0x105;
        return Integer.compareUnsigned(index, 22) <= 0 && ((0x4088c0 >>> index) & 1) != 0;
    }

    private boolean suppresses(Item item) {
        int id = Item.getId(item);
        int selected = mode.index();
        if (isSword(id)) {
            return selected != CONSUMABLE_MODE && id != 0x105 && id != 0x15a;
        }
        return id != 0x105 && id != 0x15a && selected != SWORD_MODE;
    }

    private void filter(PacketEvent event) {
        Object raw = event.packet;
        if (raw instanceof PlayerUseC2SPacket) {
            ItemStack stack = ((PlayerUseC2SPacket) raw).itemInHand;
            Item item = stack != null ? stack.getItem() : null;
            suppressRelease = item != null && suppresses(item);
            return;
        }
        if (raw instanceof PlayerHandActionC2SPacket && suppressRelease) {
            if (((PlayerHandActionC2SPacket) raw).action.ordinal() == 5) {
                event.action = 2;
            }
            suppressRelease = false;
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(mode);
    }
}
