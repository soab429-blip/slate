package yt.ren.slate.config;

import yt.ren.slate.module.Module;
import yt.ren.slate.setting.BooleanSetting;
import yt.ren.slate.setting.ChoiceSetting;
import yt.ren.slate.setting.ColorSetting;
import yt.ren.slate.setting.ListSetting;
import yt.ren.slate.setting.ListStorage;
import yt.ren.slate.setting.RangeSetting;
import yt.ren.slate.setting.ScalarSetting;
import yt.ren.slate.setting.Setting;
import yt.ren.slate.util.option.Json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SettingsConfig {
    public static final int LOAD = 0;
    public static final int SAVE = 1;
    public static final int CAPTURE_DEFAULTS = 2;
    public static final int RESET = 3;

    public static class Context {
        public Json object;
        public final int operation;
        public int failed;

        public Context(Json object, int operation) {
            this.object = object;
            this.operation = operation;
        }
    }

    private static float bound(float value, float minimum, float maximum) {
        if (minimum > value) {
            return minimum;
        }
        return Math.min(value, maximum);
    }

    private static void failed(Context c, boolean failure) {
        c.failed |= failure ? 1 : 0;
    }

    static void process(Context c, String key, BooleanSetting s) {
        if (c.failed != 0) {
            return;
        }
        switch (c.operation) {
            case LOAD: {
                int result = (s.snapshot() ? 1 : 0);
                Json value = Json.member(c.object, key);
                if (Json.isBoolean(value)) {
                    result = Json.isTrue(value) ? 1 : 0;
                }
                s.set((result) != 0);
                break;
            }
            case SAVE:
                failed(c, Json.addBoolean(c.object, key, (s.get() ? 1 : 0)) == null);
                break;
            case CAPTURE_DEFAULTS:
                s.captureSnapshot();
                break;
            default:
                s.restoreSnapshot();
                break;
        }
    }

    static void process(Context c, String key, ScalarSetting s) {
        if (c.failed != 0) {
            return;
        }
        switch (c.operation) {
            case LOAD: {
                Json value = Json.member(c.object, key);
                float result = s.snapshot();
                if (Json.isNumber(value)) {
                    float number = (float) Json.number(value);
                    if (!Float.isNaN(number)) {
                        result = bound(number, s.minimum(), s.maximum());
                    }
                }
                s.set(result);
                break;
            }
            case SAVE:
                failed(c, Json.addNumber(c.object, key, s.get()) == null);
                break;
            case CAPTURE_DEFAULTS:
                s.captureSnapshot();
                break;
            default:
                s.restoreSnapshot();
                break;
        }
    }

    static void process(Context c, String key, RangeSetting s) {
        if (c.failed != 0) {
            return;
        }
        switch (c.operation) {
            case LOAD: {
                Json value = Json.member(c.object, key);
                float low0 = s.snapshotLow();
                float high0 = s.snapshotHigh();
                if (Json.isObject(value)) {
                    float low = (float) Json.number(Json.member(value, "min"));
                    if (!Float.isNaN(low)) {
                        float high = (float) Json.number(Json.member(value, "max"));
                        if (!Float.isNaN(high)) {
                            low0 = bound(Math.min(high, low), s.minimum(), s.maximum());
                            high0 = bound(high, s.minimum(), s.maximum());
                        }
                    }
                }
                s.set(low0, high0);
                break;
            }
            case SAVE: {
                Json object = Json.addObject(c.object, key);
                if (object == null) {
                    c.failed = 1;
                } else {
                    failed(c, Json.addNumber(object, "min", s.low()) == null);
                    failed(c, Json.addNumber(object, "max", s.high()) == null);
                }
                break;
            }
            case CAPTURE_DEFAULTS:
                s.captureSnapshot();
                break;
            default:
                s.restoreSnapshot();
                break;
        }
    }

    static void process(Context c, String key, ChoiceSetting s) {
        if (c.failed != 0) {
            return;
        }
        switch (c.operation) {
            case LOAD: {
                int result = s.snapshot();
                Json value = Json.member(c.object, key);
                if (Json.isString(value)) {
                    for (int i = 0; i < s.count(); ++i) {
                        if (Arrays.equals(s.choice(i).getBytes(StandardCharsets.UTF_8), value.text)) {
                            result = i;
                            break;
                        }
                    }
                }
                s.select(result);
                break;
            }
            case SAVE:
                failed(c, Json.addString(c.object, key, s.choice(s.index()).getBytes(StandardCharsets.UTF_8)) == null);
                break;
            case CAPTURE_DEFAULTS:
                s.captureSnapshot();
                break;
            default:
                s.restoreSnapshot();
                break;
        }
    }

    static float[] readColor(Json value, float[] fallback) {
        if (Json.isObject(value)) {
            float red = (float) Json.number(Json.member(value, "r"));
            if (0.0f <= red && red <= 1.0f) {
                float green = (float) Json.number(Json.member(value, "g"));
                if (0.0f <= green && green <= 1.0f) {
                    float blue = (float) Json.number(Json.member(value, "b"));
                    if (0.0f <= blue && blue <= 1.0f) {
                        return new float[]{red, green, blue};
                    }
                }
            }
        }
        return fallback.clone();
    }

    static void process(Context c, String key, ColorSetting s) {
        if (c.failed != 0) {
            return;
        }
        switch (c.operation) {
            case LOAD: {
                float[] color = readColor(Json.member(c.object, key), s.snapshot());
                s.set(color[0], color[1], color[2]);
                break;
            }
            case SAVE: {
                Json object = Json.addObject(c.object, key);
                if (object == null) {
                    c.failed = 1;
                } else {
                    failed(c, Json.addNumber(object, "r", s.channel(0)) == null);
                    failed(c, Json.addNumber(object, "g", s.channel(1)) == null);
                    failed(c, Json.addNumber(object, "b", s.channel(2)) == null);
                }
                break;
            }
            case CAPTURE_DEFAULTS:
                s.captureSnapshot();
                break;
            default:
                s.restoreSnapshot();
                break;
        }
    }

    static void process(Context c, String key, ListSetting s) {
        if (c.failed != 0) {
            return;
        }
        ListStorage storage = s.storage();
        if (c.operation == LOAD) {
            storage.clear();
            Json value = Json.member(c.object, key);
            if (Json.isArray(value)) {
                for (Json item = value.child; item != null; item = item.next) {
                    if (s.elementKind() == 1 && Json.isObject(item)) {
                        Json name = Json.member(item, "name");
                        if (Json.isString(name)) {
                            float[] color = readColor(Json.member(item, "color"), new float[]{1.0f, 1.0f, 1.0f});
                            storage.insert(new String(name.text, StandardCharsets.UTF_8), color);
                        }
                    } else if (s.elementKind() == 0 && Json.isString(item)) {
                        storage.insert(new String(item.text, StandardCharsets.UTF_8), new float[3]);
                    }
                }
            }
        } else if (c.operation == SAVE) {
            Json array = Json.addArray(c.object, key);
            if (array == null) {
                c.failed = 1;
                return;
            }
            if (storage.count() != 0) {
                long i = 0;
                do {
                    if (s.elementKind() == 1) {
                        Json object = Json.object();
                        boolean failure = true;
                        if (Json.addString(object, "name", storage.name(i).getBytes(StandardCharsets.UTF_8)) != null) {
                            Json color = Json.addObject(object, "color");
                            if (color != null) {
                                float[] rgb = storage.color(i);
                                if (Json.addNumber(color, "r", rgb[0]) != null
                                        && Json.addNumber(color, "g", rgb[1]) != null
                                        && Json.addNumber(color, "b", rgb[2]) != null) {
                                    failure = !Json.append(array, object);
                                }
                            }
                        }
                        failed(c, failure);
                    } else if (s.elementKind() == 0) {
                        Json value = Json.string(storage.name(i).getBytes(StandardCharsets.UTF_8));
                        failed(c, !Json.append(array, value));
                    }
                    ++i;
                } while (i < storage.count());
            }
        } else if (c.operation == RESET) {
            storage.clear();
        }
    }

    public static void process(Module module, Context c) {
        for (Setting setting : module.settings()) {
            if (setting instanceof BooleanSetting) {
                process(c, setting.key(), (BooleanSetting) setting);
            } else if (setting instanceof ScalarSetting) {
                process(c, setting.key(), (ScalarSetting) setting);
            } else if (setting instanceof RangeSetting) {
                process(c, setting.key(), (RangeSetting) setting);
            } else if (setting instanceof ChoiceSetting) {
                process(c, setting.key(), (ChoiceSetting) setting);
            } else if (setting instanceof ColorSetting) {
                process(c, setting.key(), (ColorSetting) setting);
            } else if (setting instanceof ListSetting) {
                if (c.operation != CAPTURE_DEFAULTS && ((ListSetting) setting).storage() == null) {
                    c.failed = 1;
                    return;
                }
                process(c, setting.key(), (ListSetting) setting);
            }
        }
    }
}
