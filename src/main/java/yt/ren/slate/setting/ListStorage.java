package yt.ren.slate.setting;

public interface ListStorage {
    long count();

    String name(long index);

    float[] color(long index);

    boolean insert(String name, float[] color);

    void remove(long index);

    void clear();

    String[] suggestions();

    boolean acceptsInput();

    void inputCharacter(StringBuilder text, int character);
}
