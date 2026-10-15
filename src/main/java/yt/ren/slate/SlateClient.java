package yt.ren.slate;

import net.fabricmc.api.ClientModInitializer;
import yt.ren.slate.core.ClientRuntime;

public class SlateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientRuntime runtime = new ClientRuntime();
        runtime.compose();
    }
}
