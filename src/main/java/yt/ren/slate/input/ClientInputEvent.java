package yt.ren.slate.input;

public class ClientInputEvent {
    public int type;
    public int key;
    public final byte[] characters = new byte[4];
    public int state;
    public int toggle;

    public ClientInputEvent(int type, int key, int state, int toggle) {
        this.type = type;
        this.key = key;
        this.state = state;
        this.toggle = toggle;
    }

    public int mouseY() {
        return (characters[0] & 0xFF) | (characters[1] & 0xFF) << 8 | (characters[2] & 0xFF) << 16
                | (characters[3] & 0xFF) << 24;
    }

    void setMouseY(int y) {
        characters[0] = (byte) y;
        characters[1] = (byte) (y >>> 8);
        characters[2] = (byte) (y >>> 16);
        characters[3] = (byte) (y >>> 24);
    }

    public float wheel() {
        return Float.intBitsToFloat(key);
    }
}
