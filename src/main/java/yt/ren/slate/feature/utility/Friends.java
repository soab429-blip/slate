package yt.ren.slate.feature.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;
import yt.ren.slate.core.ClientChat;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.FriendChangeEvent;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.CompactList;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.SettingLists.FriendEntry;
import yt.ren.slate.setting.SettingLists;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class Friends extends Module {
    private final BooleanSetting middleClickAdd = register("middle_click_add", new BooleanSetting("Middle-click to add", 0, 0));
    private final ListSetting friendsSetting = register("friends", new ListSetting("Friends", "Enter friend username", 0));

    private final ClientRuntime runtime;
    private final CompactList<FriendEntry> friends;

    public Friends(ClientRuntime runtime) {
        super(ModuleId.FRIENDS, "Friends", "Lets you mark other players as friendly.", Category.UTILITY);
        traits(Trait.HIDDEN, Trait.GLOBAL);
        friendsSetting.attachStorage(runtime.lists.friendsStorage());
        this.runtime = runtime;
        this.friends = runtime.lists.friends;
    }

    private static long now() {
        return Math.floorDiv(System.currentTimeMillis(), 1000L);
    }

    private static FriendEntry identity(UUID value) {
        return new FriendEntry(value.getMostSignificantBits(), value.getLeastSignificantBits(), 0L, 0L);
    }

    private FriendEntry findFriend(FriendEntry value) {
        return friends.find(value, SettingLists::compareFriendIdentity);
    }

    public void middleClick(ModuleRegistry registry) {
        if (!middleClickAdd.get()) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        HitResult hit = game.crosshairTarget;
        if (hit == null) {
            return;
        }
        Entity target = hit.entity;
        if (target == null || !(target instanceof RemoteClientPlayerEntity)) {
            return;
        }
        FriendEntry entry = identity(target.uuid);
        entry.lastSeen = now();
        FriendEntry found = findFriend(entry);
        FriendChangeEvent event = new FriendChangeEvent(false);
        if (found != null) {
            friends.removeItem(found);
        } else {
            if (PlayerTags.has((PlayerEntity) target, 2)) {
                return;
            }
            event = new FriendChangeEvent(true);
            friends.insertWithDuplicates(entry, SettingLists::compareFriendIdentity);
        }
        registry.post(EventTypes.FRIEND_CHANGE, event);
    }

    void updateWorld(ClientGameContext context) {
        if (context.player == null) {
            return;
        }
        long time = now();
        List<PlayerEntity> list = context.world.players;
        int count = list.size();
        for (int index = 0; index < count; count = list.size()) {
            PlayerEntity player = list.get(index);
            if (context.player != player) {
                FriendEntry found = findFriend(identity(player.uuid));
                if (found != null) {
                    PlayerTags.add(player, 4);
                    found.lastSeen = time;
                } else {
                    PlayerTags.remove(player, 4);
                }
            }
            index++;
        }
    }

    void handleToggle(ToggleEvent event, Module descriptor) {
        if (event.module != descriptor || (event.state & 0xff) != 0) {
            return;
        }
        ClientGameContext context = (ClientGameContext) event.context;
        if (context.world == null) {
            return;
        }
        List<PlayerEntity> list = context.world.players;
        int count = list.size();
        for (int index = 0; index < count; count = list.size()) {
            PlayerEntity player = list.get(index);
            if (context.player != player) {
                PlayerTags.remove(player, 4);
            }
            index++;
        }
    }

    void cleanup() {
        friends.setCount(0);
    }

    public int count() {
        return friends.count();
    }

    private static int lowerAscii(int c) {
        return (c + (((c - 'A') & 0xFF) < 26 ? 32 : 0)) & 0xFF;
    }

    boolean findPlayer(ClientGameContext context, byte[] name, FriendEntry[] result, String[] displayName) {
        if (name.length > 16) {
            ClientChat.display("§cUsername is too long.");
            return false;
        }
        List<PlayerEntity> list = context.world.players;
        PlayerEntity selected = null;
        int count = list.size();
        for (int index = 0; index < count; count = list.size()) {
            PlayerEntity player = list.get(index);
            if (player != context.player) {
                byte[] bytes = player.getName().getBytes(StandardCharsets.UTF_8);
                int length = bytes.length;
                if (name.length <= length) {
                    boolean matched = true;
                    for (int i = 0; i < name.length; ++i) {
                        if (lowerAscii(name[i] & 0xFF) != lowerAscii(bytes[i] & 0xFF)) {
                            matched = false;
                            break;
                        }
                    }
                    if (matched) {
                        if (selected != null) {
                            ClientChat.display("§cFound multiple names starting with '%s'.",
                                    new String(name, StandardCharsets.UTF_8));
                            return false;
                        }
                        int copied = Math.min(length, 16);
                        displayName[0] = new String(bytes, 0, copied, StandardCharsets.UTF_8);
                        selected = player;
                    }
                }
            }
            index++;
        }
        if (selected != null) {
            result[0] = identity(selected.uuid);
            return true;
        }
        ClientChat.display("§cPlayer not found.");
        return false;
    }

    public boolean commandAdd(ClientGameContext context, byte[] name) {
        FriendEntry[] entry = new FriendEntry[1];
        String[] display = new String[1];
        if (!findPlayer(context, name, entry, display)) {
            return false;
        }
        friends.insert(entry[0], SettingLists::compareFriendIdentity);
        ClientChat.display("§aAdded %s as friend.", display[0]);
        return true;
    }

    public boolean commandRemove(ClientGameContext context, byte[] name) {
        FriendEntry[] entry = new FriendEntry[1];
        String[] display = new String[1];
        if (!findPlayer(context, name, entry, display)) {
            return false;
        }
        FriendEntry found = findFriend(entry[0]);
        if (found != null) {
            friends.removeItem(found);
        }
        ClientChat.display("§aRemoved %s as friend.", display[0]);
        return true;
    }

    private static double unsignedRange(long value) {
        double low = Double.longBitsToDouble(0x4330000000000000L | (value & 0xFFFFFFFFL)) - 4503599627370496.0;
        double high = Double.longBitsToDouble(0x4530000000000000L | (value >>> 32)) - 19342813113834066795298816.0;
        return high + low;
    }

    public boolean commandNearby(ClientGameContext context, long range, int replace) {
        int previous = friends.count();
        if (replace == 1) {
            friends.setCount(0);
        }
        PlayerEntity origin = context.player;
        List<PlayerEntity> list = context.world.players;
        int count = list.size();
        long added = 0;
        if (count > 0) {
            double radius = unsignedRange(range);
            double limit = radius * radius;
            for (int index = 0; index < count; count = list.size()) {
                PlayerEntity player = list.get(index);
                if (player != context.player && !PlayerTags.has(player, 2)) {
                    double x = origin.x - player.x;
                    double y = origin.y - player.y;
                    double z = origin.z - player.z;
                    double squared = z * z + (x * x + y * y);
                    if (!(squared > limit)) {
                        friends.insert(identity(player.uuid), SettingLists::compareFriendIdentity);
                        ++added;
                    }
                }
                index++;
            }
        }
        String suffix = added == 1 ? "" : "s";
        if (replace != 0) {
            ClientChat.display("§aRemoved %zu, added %zu friend%s.", (long) previous, added, suffix);
        } else {
            ClientChat.display("§aAdded %zu friend%s.", added, suffix);
        }
        return true;
    }

    public boolean commandClear(ClientGameContext context) {
        int count = friends.count();
        ClientChat.display("§aRemoved %zu friend%s.", (long) count, count == 1 ? "" : "s");
        friends.setCount(0);
        return true;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> updateWorld((ClientGameContext) e.context), 10000);
        on(EventTypes.MODULE_TOGGLE, e -> handleToggle(e, this));
        on(EventTypes.UNLOAD, e -> cleanup());
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(middleClickAdd);
    }
}
