package yt.ren.slate.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Move"),
    VISUAL("Visual"),
    BLOCK("Block"),
    UTILITY("Utility");

    private final String title;

    Category(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
