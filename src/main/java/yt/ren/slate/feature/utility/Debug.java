package yt.ren.slate.feature.utility;

import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import yt.ren.slate.core.ClientChat;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.ClickEvent;
import yt.ren.slate.event.Events.PacketEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;

public class Debug extends Module {
    private final BooleanSetting clicks = register("clicks", new BooleanSetting("Clicks", 0, 0));
    private final BooleanSetting hurtTime = register("hurt_time", new BooleanSetting("Hurt time", 0, 0));
    private final BooleanSetting itemUseState = register("item_use_state", new BooleanSetting("Server item use state", 0, 0));

    private static final String HURT_FORMAT = "§7[%03d] [debug/hurt_time] §%c%02d §%cS §%cB";
    private static final String USE_FORMAT = "§7[%03d] [debug/item_use] §%s";
    private static final String START_TEXT = "aStart";
    private static final String STOP_TEXT = "cStop";
    private static final String CLICK_FORMAT = "§7[%03d] [debug/click] §%cL %u §%cR %u";
    private static final int[] HURT_COLORS = {97, 101, 99, 52};


    private int reportedUse;
    private volatile int currentUse;
    private int attackClicks;
    private int useClicks;
    private int idleFrames;

    public Debug(ClientRuntime runtime) {
        super(ModuleId.DEBUG, "Debug", "Prints debug information in chat.", Category.UTILITY);
        traits(Trait.HIDDEN);
    }


    private static int tickLabel(LocalClientPlayerEntity player) {
        return Integer.remainderUnsigned(player.ticks, 1000);
    }

    void tick(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        LocalClientPlayerEntity player = c.player;
        if (hurtTime.get()) {
            int hurt = player.damagedTimer;
            if (hurt >= 7) {
                int ticks = tickLabel(player);
                int index = hurt - 7;
                int color = Integer.compareUnsigned(index, 4) < 0 ? HURT_COLORS[index] : 'f';
                int sprintColor = player.isSprinting() ? '8' : 'c';
                int blockColor = player.hasItemInUse() ? 'c' : '8';
                ClientChat.display(HURT_FORMAT, ticks, color, hurt, sprintColor, blockColor);
            }
        }
        if (itemUseState.get()) {
            int current = currentUse;
            if ((current & 1) != reportedUse) {
                reportedUse = current & 1;
                int ticks = tickLabel(player);
                ClientChat.display(USE_FORMAT, ticks, (current & 1) != 0 ? START_TEXT : STOP_TEXT);
            }
        }
    }

    void observePacket(PacketEvent event) {
        if (event.action != 3 && event.action != 0) {
            return;
        }
        Object packet = event.packet;
        if (packet instanceof PlayerUseC2SPacket) {
            currentUse = 1;
            return;
        }
        if (packet instanceof PlayerHandActionC2SPacket
                && ((PlayerHandActionC2SPacket) packet).getAction().ordinal() == 5) {
            currentUse = 0;
        }
    }

    void observeClick(ClickEvent event) {
        if (!clicks.get()) {
            return;
        }
        if (event.left != 0) {
            ++attackClicks;
        } else {
            ++useClicks;
        }
        idleFrames = 0;
    }

    void frame(ClientGameContext c) {
        if (c.focused != 1) {
            return;
        }
        int age = idleFrames;
        if (clicks.get()) {
            if (Integer.compareUnsigned(age, 10) < 0) {
                int leftColor = attackClicks == 0 ? '8' : attackClicks == 1 ? 'a' : '2';
                int rightColor = useClicks == 0 ? '8' : useClicks == 1 ? 'b' : '3';
                int ticks = tickLabel(c.player);
                ClientChat.display(CLICK_FORMAT, ticks, leftColor, Integer.toUnsignedLong(attackClicks), rightColor,
                        Integer.toUnsignedLong(useClicks));
                age = idleFrames;
            }
            attackClicks = 0;
            useClicks = 0;
        }
        idleFrames = age + 1;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> tick((ClientGameContext) e.context));
        on(EventTypes.PACKET_SEND, e -> observePacket(e), -10000);
        on(EventTypes.CLICK_POST, e -> observeClick(e));
        on(EventTypes.UPDATE_POST, e -> frame((ClientGameContext) e.context));
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(clicks);
        w.show(hurtTime);
        w.show(itemUseState);
    }
}
