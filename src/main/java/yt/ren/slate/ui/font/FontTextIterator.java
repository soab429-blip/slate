package yt.ren.slate.ui.font;

public class FontTextIterator {
    public float x;
    public float y;
    public float nextX;
    public float nextY;
    public float scale;
    public float spacing;
    public int codepoint;
    public short size;
    public short blur;
    FontStash.Font font;
    public int previousGlyph;
    public byte[] text;
    public int string;
    public int next;
    public int end;
    public int utf8State;
    public int bitmapOption;

    public void set(FontTextIterator o) {
        x = o.x;
        y = o.y;
        nextX = o.nextX;
        nextY = o.nextY;
        scale = o.scale;
        spacing = o.spacing;
        codepoint = o.codepoint;
        size = o.size;
        blur = o.blur;
        font = o.font;
        previousGlyph = o.previousGlyph;
        text = o.text;
        string = o.string;
        next = o.next;
        end = o.end;
        utf8State = o.utf8State;
        bitmapOption = o.bitmapOption;
    }

    public FontTextIterator copy() {
        FontTextIterator result = new FontTextIterator();
        result.set(this);
        return result;
    }

    void clear() {
        x = 0.0f;
        y = 0.0f;
        nextX = 0.0f;
        nextY = 0.0f;
        scale = 0.0f;
        spacing = 0.0f;
        codepoint = 0;
        size = 0;
        blur = 0;
        font = null;
        previousGlyph = 0;
        text = null;
        string = 0;
        next = 0;
        end = 0;
        utf8State = 0;
        bitmapOption = 0;
    }
}
