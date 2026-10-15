package yt.ren.slate.game;

import yt.ren.slate.core.ClientGameContext;

public class Controls {
    public static boolean attackPressed(ClientGameContext c) {
        int code = c.client.options.keyBindings[0].keyCode;
        return c.keys[code == -99 ? 2 : 1] != 0;
    }
}
