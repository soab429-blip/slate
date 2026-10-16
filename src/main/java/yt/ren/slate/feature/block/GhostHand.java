package yt.ren.slate.feature.block;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.Controls;
import yt.ren.slate.game.HeldItem;
import yt.ren.slate.game.MouseOver;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

public class GhostHand extends Module {
    private final BooleanSetting nonPlayers = register("non_players", new BooleanSetting("Non-player entities", 1, 0));
    private final BooleanSetting friendlies = register("friendlies", new BooleanSetting("Friendlies", 1, 0));
    private final BooleanSetting bots = register("bots", new BooleanSetting("Bots", 1, 0));
    private final BooleanSetting enemies = register("enemies", new BooleanSetting("Enemies", 1, 0));
    private final BooleanSetting allowUsingSword = register("allow_using_sword", new BooleanSetting("Sword", 1, 0));
    private final BooleanSetting allowUsingTools = register("allow_using_tools", new BooleanSetting("Tool", 1, 0));
    private final BooleanSetting allowUsingFists = register("allow_using_fists", new BooleanSetting("Fists", 1, 0));
    private final BooleanSetting allowUsingBuckets = register("allow_using_buckets", new BooleanSetting("Bucket", 1, 0));
    private final BooleanSetting allowUsingFns = register("allow_using_fns", new BooleanSetting("Flint and steel", 1, 0));
    private final BooleanSetting allowUsingCobweb = register("allow_using_cobweb", new BooleanSetting("Cobweb", 1, 0));
    private final BooleanSetting allowUsingOther = register("allow_using_other", new BooleanSetting("Other", 1, 0));
    private final BooleanSetting requireMousePressed = register("require_mouse_pressed", new BooleanSetting("Mouse pressed", 0, 0));

    private final ClientRuntime runtime;

    public GhostHand(ClientRuntime runtime) {
        super(ModuleId.GHOST_HAND, "Ghost Hand", "Allows interacting with blocks through entities.", Category.BLOCK);
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.MOUSE_OVER, MouseOver::requestCustomMouseOver);
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
            case 0x103:
                return allowUsingFns;
            case 0x10b:
            case 0x10c:
            case 0x110:
            case 0x114:
            case 0x11b:
                return allowUsingSword;
            case 0x145:
            case 0x146:
            case 0x147:
                return allowUsingBuckets;
            case 0x1e:
                return allowUsingCobweb;
            default:
                return allowUsingOther;
        }
    }

    public boolean allowsThrough(ClientGameContext c, Entity target) {
        if (!isEnabled() || (requireMousePressed.get() && !Controls.attackPressed(c))) {
            return false;
        }
        if (!allowanceFor(HeldItem.selected(c.player.inventory)).get()) {
            return false;
        }
        PlayerEntity player = MouseOver.asPlayer(target);
        if (player == null) {
            return nonPlayers.get();
        }
        int marked = PlayerTags.tags(player);
        return ((marked & 2) != 0 ? bots : (marked & 5) != 0 ? friendlies : enemies).get();
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Interact through");
        w.show(nonPlayers);
        w.show(friendlies);
        w.show(bots);
        w.show(enemies);
        w.heading("Allow while using");
        w.show(allowUsingSword);
        w.show(allowUsingTools);
        w.show(allowUsingFists);
        w.show(allowUsingBuckets);
        w.show(allowUsingFns);
        w.show(allowUsingCobweb);
        w.show(allowUsingOther);
        w.heading("Conditions");
        w.show(requireMousePressed);
    }
}
