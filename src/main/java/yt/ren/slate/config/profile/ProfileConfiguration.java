package yt.ren.slate.config.profile;

import yt.ren.slate.config.SettingsConfig;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.module.Trait;
import yt.ren.slate.ui.SettingsWidgets;
import yt.ren.slate.util.math.Sse;
import yt.ren.slate.util.option.Json;

import java.nio.charset.StandardCharsets;

public class ProfileConfiguration {
    private final ModuleRegistry modules;
    private final ProfileFiles files;
    public ProfileRegistry profiles;

    public byte[] activeName;

    public ProfileConfiguration(ModuleRegistry modules, ProfileFiles files) {
        this.modules = modules;
        this.files = files;
    }

    private static byte[] b(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private boolean hasSettings(Module module) {
        return module.persistsSettings();
    }

    private void process(Module module, SettingsConfig.Context context) {
        SettingsConfig.process(module, context);
    }

    public static int readBind(Json value) {
        if (!Json.isNumber(value)) {
            return 0;
        }
        int converted = (int) Sse.cvttsd2si64(value.number + 0.5);
        return Integer.compareUnsigned(converted - 104, 0xffffff99) >= 0 ? converted : 0;
    }

    boolean loadModule(Json root, Module module) {
        Json object = Json.member(root, b(module.name()));
        if (!module.has(Trait.NO_TOGGLE)) {
            modules.queueModuleChange(module, Json.isTrue(Json.member(object, "__on")) ? 1 : 0);
        }
        if (!module.has(Trait.NO_KEYBIND)) {
            modules.context.change(module, readBind(Json.member(object, "__bind")));
            module.setHoldToActivate((module.has(Trait.NO_HOLD) ? 0 : (Json.isTrue(Json.member(object, "__hold")) ? 1 : 0)) != 0);
        }
        if (!hasSettings(module)) {
            return false;
        }
        SettingsConfig.Context context = new SettingsConfig.Context(object, SettingsConfig.LOAD);
        process(module, context);
        return context.failed != 0;
    }

    boolean saveModule(Json root, Module module) {
        Json object = Json.addObject(root, b(module.name()));
        if (!module.has(Trait.NO_TOGGLE) && module.isEnabled()) {
            if (Json.addBoolean(object, "__on", 1) == null) {
                return true;
            }
        }
        if (!module.has(Trait.NO_KEYBIND)) {
            if (module.keybind() != 0 && Json.addNumber(object, "__bind", (double) Integer.toUnsignedLong(module.keybind())) == null) {
                return true;
            }
            if (!module.has(Trait.NO_HOLD) && module.holdToActivate()) {
                if (Json.addBoolean(object, "__hold", 1) == null) {
                    return true;
                }
            }
        }
        if (!hasSettings(module)) {
            return false;
        }
        SettingsConfig.Context context = new SettingsConfig.Context(object, SettingsConfig.SAVE);
        process(module, context);
        return context.failed != 0;
    }

    public void captureDefaults() {
        for (Module module : modules.all) {
            if (hasSettings(module)) {
                process(module, new SettingsConfig.Context(null, SettingsConfig.CAPTURE_DEFAULTS));
            }
        }
    }

    public void loadGlobalText(byte[] text) {
        Json root = Json.parse(text);
        if (root == null) {
            return;
        }
        for (Module module : modules.all) {
            if (module.has(Trait.GLOBAL)) {
                if (loadModule(root, module)) {
                    return;
                }
            }
        }
        Json bindings = Json.member(root, "profile_binds");
        if (Json.isArray(bindings)) {
            for (Json item = bindings.child; item != null; item = item.next) {
                Json name = Json.member(item, "name");
                if (Json.isString(name) && name.text != null) {
                    ProfileRegistry.Entry profile = profiles.find(name.text);
                    if (profile != null) {
                        profile.bindCode = readBind(Json.member(item, "bind"));
                    }
                }
            }
        }
    }

    public byte[] saveGlobalText() {
        Json root = Json.object();
        for (Module module : modules.all) {
            if (module.has(Trait.GLOBAL)) {
                if (saveModule(root, module)) {
                    return null;
                }
            }
        }
        Json array = Json.addArray(root, "profile_binds");
        for (ProfileRegistry.Entry profile : profiles.entries) {
            if (profile.bindCode != 0) {
                Json item = Json.object();
                if (Json.addString(item, "name", profile.name) == null
                        || Json.addNumber(item, "bind", (double) Integer.toUnsignedLong(profile.bindCode)) == null
                        || !Json.append(array, item)) {
                    return null;
                }
            }
        }
        return Json.print(root);
    }

    boolean loadProfileText(byte[] text) {
        Json root = Json.parse(text);
        if (root == null) {
            return false;
        }
        for (Module module : modules.all) {
            if (!module.has(Trait.GLOBAL)) {
                if (loadModule(root, module)) {
                    return false;
                }
            }
        }
        return true;
    }

    byte[] saveProfileText() {
        Json root = Json.object();
        for (Module module : modules.all) {
            if (!module.has(Trait.GLOBAL)) {
                if (saveModule(root, module)) {
                    return null;
                }
            }
        }
        return Json.print(root);
    }

    void setActive(byte[] name) {
        activeName = name == null ? null : name.clone();
    }

    public void loadGlobalFile() {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, "settings");
        }
        byte[][] text = new byte[1][];
        if (status == 0) {
            status = files.read(path, text);
        }
        if (status == 0) {
            loadGlobalText(text[0]);
        }
    }

    public int saveGlobalFile() {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, "settings");
        }
        if (status == 0) {
            byte[] text = saveGlobalText();
            status = text == null ? 5000 : files.write(path, text, 1);
        }
        return status;
    }

    private static String component(byte[] name) {
        return new String(name, StandardCharsets.ISO_8859_1);
    }

    public int loadProfile(byte[] name) {
        if (name == null) {
            for (Module module : modules.all) {
                if (module.has(Trait.GLOBAL)) {
                    continue;
                }
                modules.context.change(module, 0);
                module.setHoldToActivate(false);
                if (!module.has(Trait.NO_TOGGLE)) {
                    modules.queueModuleChange(module, 0);
                }
                if (hasSettings(module)) {
                    process(module, new SettingsConfig.Context(null, SettingsConfig.RESET));
                }
            }
            activeName = null;
            SettingsWidgets.dirty = 0;
            return 0;
        }
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, "profiles");
        }
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, component(name));
        }
        byte[][] text = new byte[1][];
        if (status == 0) {
            status = files.read(path, text);
        }
        if (status == 0) {
            status = 5001;
            if (loadProfileText(text[0])) {
                setActive(name);
                SettingsWidgets.dirty = 0;
                status = 0;
            }
        }
        return status;
    }

    public int saveProfile(byte[] name, int overwrite) {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, "profiles");
        }
        if (status == 0) {
            status = files.append(path, component(name));
        }
        if (status == 0) {
            byte[] text = saveProfileText();
            if (text == null) {
                status = 5000;
            } else {
                status = files.write(path, text, overwrite);
                if (status == 0) {
                    setActive(name);
                    SettingsWidgets.dirty = 0;
                }
            }
        }
        return status;
    }

    public int deleteProfile(byte[] name) {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.append(path, "profiles");
        }
        if (status == 0) {
            status = files.append(path, component(name));
        }
        if (status == 0) {
            status = files.remove(path);
        }
        return status;
    }

    public int exploreProfiles() {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.append(path, "profiles");
        }
        if (status == 0) {
            status = files.explore(path);
        }
        return status;
    }

    public int enumerateProfiles(ProfileFiles.Callback callback) {
        ProfileFiles.PathHolder path = new ProfileFiles.PathHolder();
        int status = files.home(path);
        if (status != 0) {
            return status;
        }
        status = files.append(path, ".slinky");
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.append(path, "profiles");
        }
        if (status == 0) {
            status = files.ensureDirectory(path);
        }
        if (status == 0) {
            status = files.enumerate(path, callback);
        }
        return status;
    }
}
