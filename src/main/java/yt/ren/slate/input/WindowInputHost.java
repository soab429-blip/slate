package yt.ren.slate.input;

public class WindowInputHost {
    private static volatile WindowInput input;
    private static volatile boolean removed;

    public static void install(WindowInput value) {
        input = value;
    }

    public static void installOnce(WindowInput value) {
        if (input == null && !removed) {
            input = value;
        }
    }

    public static void remove() {
        removed = true;
        input = null;
    }

    public static WindowInput input() {
        return input;
    }
}
