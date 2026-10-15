package yt.ren.slate.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.text.LiteralText;
import yt.ren.slate.text.ValueFormatter;

import java.nio.charset.StandardCharsets;

public class ClientChat {
    public static void display(String format, Object... args) {
        LocalClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String text = truncate(ValueFormatter.format(format, args));
        player.sendMessage(new LiteralText(text));
    }

    static String truncate(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 255) {
            return text;
        }
        byte[] cut = new byte[255];
        System.arraycopy(bytes, 0, cut, 0, 255);
        return new String(cut, StandardCharsets.UTF_8);
    }
}
