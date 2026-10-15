package yt.ren.slate.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;

public class ChannelBridge {
    public static int install(Minecraft game) {
        if (game == null) {
            return 1020;
        }
        ClientPlayNetworkHandler handler = game.getNetworkHandler();
        if (handler == null) {
            return 1021;
        }
        Channel channel = handler.getConnection().channel;
        if (channel == null) {
            return 1021;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline == null) {
            return 1021;
        }
        if (pipeline.get(ChannelHandler.class) != null) {
            return 0;
        }
        pipeline.addBefore("packet_handler", "slinky_handler", new ChannelHandler());
        return 0;
    }

    public static int remove(Minecraft game) {
        if (game == null) {
            return 1020;
        }
        ClientPlayNetworkHandler handler = game.getNetworkHandler();
        if (handler == null) {
            return 1021;
        }
        Channel channel = handler.getConnection().channel;
        if (channel == null) {
            return 1021;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline == null) {
            return 1021;
        }
        if (pipeline.get("slinky_handler") == null) {
            return 0;
        }
        pipeline.remove("slinky_handler");
        return 0;
    }
}
