package yt.ren.slate.feature.utility;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.NameColors;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.CompactList;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

import java.util.List;
import java.util.UUID;

public class AntiBot extends Module {
    private static class Entry {
        final long uuidMost;
        final long uuidLeast;
        double horizontalTravelSquared;
        boolean oldEnough;
        boolean visibilityChecked;
        boolean validName;

        Entry(long most, long least) {
            this.uuidMost = most;
            this.uuidLeast = least;
        }
    }

    private final ChoiceSetting invalidUuid = register("invalid_uuid", new ChoiceSetting("Invalid UUID", 3, 0, new String[]{"Off", "Stationary only", "Always"}, 1, 0, 0));
    private final BooleanSetting invalidUsername = register("invalid_username", new BooleanSetting("Invalid username", 1, 0));
    private final BooleanSetting alwaysInvisible = register("always_invis", new BooleanSetting("Always invisible", 1, 0));
    private final BooleanSetting alwaysStationary = register("always_stationary", new BooleanSetting("Always stationary", 0, 0));
    private final BooleanSetting sleeping = register("sleeping", new BooleanSetting("Sleeping", 0, 0));
    private final ScalarSetting minimumAge = register("min_age", new ScalarSetting("Minimum entity age", "%.0f s", 20.0f, 0.05f, 0.0f, 200.0f, 0.0f, 0.0f));

    private int worldIdentity;
    private CompactList<Entry> history = new CompactList<>();

    public AntiBot(ClientRuntime runtime) {
        super(ModuleId.ANTI_BOT, "Anti Bot", "Ignores NPCs for specific modules.", Category.UTILITY);
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()), 10000);
        on(EventTypes.MODULE_TOGGLE, this::toggled);
    }

    private static int compareIdentity(Entry a, Entry b) {
        long[] x = {a.uuidMost, a.uuidLeast};
        long[] y = {b.uuidMost, b.uuidLeast};
        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < 8; i++) {
                int bx = (int) (x[w] >>> (8 * i)) & 0xFF;
                int by = (int) (y[w] >>> (8 * i)) & 0xFF;
                if (bx != by) {
                    return bx < by ? -1 : 1;
                }
            }
        }
        return 0;
    }

    private static boolean wearsArmor(PlayerEntity player) {
        for (int slot = 36; slot < 40; ++slot) {
            ItemStack stack = player.inventory.getItem(slot);
            Item item = stack != null ? stack.getItem() : null;
            if (item != null && Item.getId(item) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean validName(String name) {
        for (int index = 0; index < name.length(); ) {
            int value = name.charAt(index);
            if (value == 0xa7) {
                index += 2;
            } else {
                if (NameColors.nameCharacter(value)) {
                    return true;
                }
                index += 1;
            }
        }
        return false;
    }

    private boolean isBot(ClientGameContext c, PlayerEntity player, Entry entry, double travel) {
        if (alwaysStationary.get() && travel < 1.0) {
            return true;
        }
        int uuidCheck = invalidUuid.index();
        if (uuidCheck != 0 && (entry.uuidMost & 0xf000L) == 0x2000L && (uuidCheck == 2 || travel < 1.0)) {
            return true;
        }
        if (sleeping.get() && player.sleeping) {
            return true;
        }
        int age = player.ticks;
        if (alwaysInvisible.get() && !entry.visibilityChecked) {
            if (player.isInvisibleTo(c.player) && !wearsArmor(player)) {
                return true;
            }
            if (age >= 21) {
                entry.visibilityChecked = true;
            }
        }
        float minimum = minimumAge.get();
        if (minimum > 0.0f && !entry.oldEnough) {
            if (minimum > (float) age) {
                return true;
            }
            entry.oldEnough = true;
        }
        if (invalidUsername.get() && !entry.validName) {
            entry.validName = validName(player.getName());
            return !entry.validName;
        }
        return false;
    }

    private void update(ClientGameContext c) {
        int identity = c.world != null ? c.world.hashCode() : 0;
        if (identity != worldIdentity) {
            history = new CompactList<>();
            worldIdentity = identity;
        }
        if (c.world == null) {
            return;
        }
        List<PlayerEntity> players = c.world.players;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity player = players.get(index);
            if (c.player == player) {
                continue;
            }
            UUID uuid = player.uuid;
            Entry entry = history.insert(new Entry(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()),
                    AntiBot::compareIdentity);
            double dx = player.lastX - player.x;
            double dz = player.lastZ - player.z;
            entry.horizontalTravelSquared += dx * dx + dz * dz;
            if (isBot(c, player, entry, entry.horizontalTravelSquared)) {
                PlayerTags.add(player, PlayerTags.BOT);
            } else {
                PlayerTags.remove(player, PlayerTags.BOT);
            }
        }
    }

    private void toggled(ToggleEvent event) {
        if (event.module == this && (event.state & 0xff) == 0) {
            history = new CompactList<>();
            ClientGameContext c = (ClientGameContext) event.context;
            PlayerTags.clear(c.world, c.player, PlayerTags.BOT);
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(invalidUuid);
        w.show(invalidUsername);
        w.show(alwaysInvisible);
        w.show(alwaysStationary);
        w.show(sleeping);
        w.show(minimumAge);
    }
}
