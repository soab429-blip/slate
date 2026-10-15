package yt.ren.slate.platform;

import org.lwjgl.sdl.SDLMisc;

import java.io.IOException;

public class SdlPlatform {
    public static void open(String uri) throws IOException {
        if (!SDLMisc.SDL_OpenURL(uri)) {
            throw new IOException("SDL could not open " + uri);
        }
    }
}
