package yt.ren.slate.ui;

public class UICallbacks {
    @FunctionalInterface
    public interface CaptureCharacter {
        void append(UIState state, int offset, int character, Object context);
    }

    @FunctionalInterface
    public interface CaptureBinding {
        void bind(Object argument, int key);
    }

    @FunctionalInterface
    public interface PopupText {
        void accept(Object owner, byte[] text);
    }

    @FunctionalInterface
    public interface PopupColor {
        void accept(Object owner, float[] color);
    }

    @FunctionalInterface
    public interface PopupConfirm {
        void confirm(Object owner);
    }

    @FunctionalInterface
    public interface PopupSuggestion {
        void accept(Object owner, long index, byte[] text);
    }

    public interface PopupActions {
        void closePopup(UIState state);
    }
}
