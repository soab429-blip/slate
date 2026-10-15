package yt.ren.slate.feature.player;

import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Text;
import yt.ren.slate.core.ClientGameContext;
import yt.ren.slate.core.ClientRuntime;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.game.NameColors;
import yt.ren.slate.module.Category;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleRegistry.ToggleEvent;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.PlayerTags;

import java.util.List;

public class Teams extends Module {
    private final BooleanSetting matchSelfColor = register("match_self_color", new BooleanSetting("Match own nametag", 1, 0));
    private final ListSetting teamColors = register("team_colors", new ListSetting("Colors", "Search color", 0));

    private final ClientRuntime runtime;

    public Teams(ClientRuntime runtime) {
        super(ModuleId.TEAMS, "Teams", "Ignores teammates for specific modules.", Category.UTILITY);
        teamColors.attachStorage(runtime.lists.teamsStorage());
        this.runtime = runtime;
    }

    @Override
    protected void setup() {
        on(EventTypes.TICK, e -> update(e.game()), 10000);
        on(EventTypes.MODULE_TOGGLE, this::toggled);
        on(EventTypes.UNLOAD, e -> runtime.lists.teams.setCount(0));
    }

    private static int ownColor(ClientGameContext c) {
        PlayerInfo info = c.client.getNetworkHandler().onlinePlayers.get(c.player.uuid);
        if (info != null) {
            Text display = info.displayName;
            if (display != null) {
                int color = NameColors.prefixColor(display.getFormattedString());
                return color != 0 ? color : 'r';
            }
            Team team = info.getTeam();
            if (team != null) {
                return NameColors.teamColor(team);
            }
        }
        return NameColors.entityColor(c.player);
    }

    private boolean listed(int color) {
        int low = color & 0xFF;
        int adjustment = (byte) low >= 'a' ? 0xa9 : 0xd0;
        byte wanted = (byte) (low + adjustment);
        return runtime.lists.teams.find(wanted, (a, b) -> a - b) != null;
    }

    private void update(ClientGameContext c) {
        if (c.player == null) {
            return;
        }
        int own = ownColor(c);
        List<PlayerEntity> players = c.world.players;
        for (int index = 0; index < players.size(); index++) {
            PlayerEntity player = players.get(index);
            if (c.player == player) {
                continue;
            }
            int color = NameColors.entityColor(player);
            if ((matchSelfColor.get() && color == own) || listed(color)) {
                PlayerTags.add(player, PlayerTags.TEAM);
            } else {
                PlayerTags.remove(player, PlayerTags.TEAM);
            }
        }
    }

    private void toggled(ToggleEvent event) {
        if (event.module == this && (event.state & 0xff) == 0) {
            ClientGameContext c = (ClientGameContext) event.context;
            PlayerTags.clear(c.world, c.player, PlayerTags.TEAM);
        }
    }

    @Override
    public void layout(SettingsWidgets w) {
        w.heading("Main");
        w.show(matchSelfColor);
        w.show(teamColors);
    }
}
