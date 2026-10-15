package yt.ren.slate.core;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.BufferUtils;
import yt.ren.slate.aim.AimRandom;
import yt.ren.slate.aim.AimState;
import yt.ren.slate.aim.rotation.RotationLifecycle;
import yt.ren.slate.aim.rotation.SharedRotation;
import yt.ren.slate.config.profile.ProfileConfiguration;
import yt.ren.slate.config.profile.ProfileFiles;
import yt.ren.slate.config.profile.ProfileRegistry;
import yt.ren.slate.event.EventTypes;
import yt.ren.slate.event.Events.BindingEvent;
import yt.ren.slate.event.Events;
import yt.ren.slate.feature.combat.Piercing;
import yt.ren.slate.feature.player.Friends;
import yt.ren.slate.feature.player.ItemUse;
import yt.ren.slate.feature.world.GhostHand;
import yt.ren.slate.game.MouseOver;
import yt.ren.slate.game.attack.AttackTargetCache;
import yt.ren.slate.hook.ClientHooks;
import yt.ren.slate.input.ClientInput;
import yt.ren.slate.input.ClientInputEvent;
import yt.ren.slate.input.KeyBindings;
import yt.ren.slate.input.WindowInput;
import yt.ren.slate.module.Module;
import yt.ren.slate.module.ModuleId;
import yt.ren.slate.module.ModuleList;
import yt.ren.slate.module.ModuleRegistry;
import yt.ren.slate.net.ChannelBridge;
import yt.ren.slate.setting.SettingLists;
import yt.ren.slate.ui.UIDrawing;
import yt.ren.slate.ui.UIModule;
import yt.ren.slate.ui.UISurface;
import yt.ren.slate.util.Clock;

import java.nio.FloatBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientRuntime {
    private final Map<Class<?>, Module> byType = new HashMap<>();
    private static ClientRuntime instance;

    public final SettingLists lists = new SettingLists();
    public final AimRandom random = new AimRandom();

    public final double[] frameTime = new double[1];
    public final Clock clock = new Clock();
    public final Module[] owner = new Module[1];
    public final SharedRotation sharedRotation = new SharedRotation();
    public final ItemUse.State itemUse = new ItemUse.State(owner);
    public final RotationLifecycle rotation = new RotationLifecycle(sharedRotation);

    public final AimState aimAdjustment = new AimState();

    public final Module[] modules = new Module[ModuleId.values().length];
    public ModuleRegistry registry;
    public final ClientFrame.State frameDriverState = new ClientFrame.State();

    public final byte[] keys = new byte[104];
    public final AttackTargetCache targetCache = new AttackTargetCache();

    public int guiEnabled() {
        return (module(ModuleId.GUI).isEnabled() ? 1 : 0);
    }

    public Object profiles;
    public ProfileRegistry profileRegistry;
    public ProfileConfiguration configuration;
    public KeyBindings profileKeys;
    public KeyBindings moduleKeys;

    public final AtomicInteger lock = new AtomicInteger();
    public volatile int bypass;

    public UISurface surface;
    public int overlayWidth;
    public int overlayHeight;
    public final FloatBuffer savedModelview = BufferUtils.createFloatBuffer(16);
    public final FloatBuffer savedProjection = BufferUtils.createFloatBuffer(16);
    public double lastGuiTime = -1.0;
    public int previousFocused;
    public int redrawCountdown;
    public int renderFlag;
    public int redrawRequested;
    public final AtomicBoolean initialized = new AtomicBoolean();

    public MouseOver mouseOver;
    public Friends friends;
    public UIModule gui;
    public final WindowInput windowInput = new WindowInput();
    public ClientInput.UIInputConsumer guiInput = event -> 0;

    public PlayerEntity aimTargetReference;


    public static ClientRuntime get() {
        return instance;
    }

    public ClientRuntime() {
        instance = this;
        Module[] created = ModuleList.create(this);
        System.arraycopy(created, 0, modules, 0, created.length);
    }

    public Module module(ModuleId id) {
        return modules[id.ordinal()];
    }

    public <T extends Module> T module(Class<T> type) {
        Module module = byType.get(type);
        if (module == null) {
            for (Module candidate : modules) {
                if (type.isInstance(candidate)) {
                    byType.put(type, candidate);
                    module = candidate;
                    break;
                }
            }
        }
        return type.cast(module);
    }

    public void compose() {
        gui = (UIModule) module(ModuleId.GUI);
        friends = (Friends) module(ModuleId.FRIENDS);
        mouseOver = new MouseOver(module(Piercing.class), module(GhostHand.class));
        for (Module module : modules) {
            module.initialize();
        }
        registry = new ModuleRegistry(modules);
        registry.context = this::changeModuleBinding;
        ClientHooks.commands = new Commands(friends, module(ModuleId.GUI));
        ClientHooks.channelInstaller = ChannelBridge::install;
        windowInput.connect(this::handleClientInput);
        ClientHooks.initializer = ClientRuntime::firstCallInitialize;
        profileRegistry = new ProfileRegistry();
        profiles = profileRegistry;
        configuration = new ProfileConfiguration(registry, new ProfileFiles());
        configuration.profiles = profileRegistry;
        configuration.captureDefaults();
        profileKeys = new KeyBindings((value, key, state) -> handleProfileBinding((byte[]) value, key, state), null, 0);
        moduleKeys = new KeyBindings((value, key, state) -> handleModuleBinding((Module) value, state), null, 1);
    }

    public void changeModuleBinding(Module module, int key) {
        moduleKeys.remove(module.keybind(), module);
        module.setKeybind(key);
        moduleKeys.insert(key, module);
        registry.post(EventTypes.BINDING, new BindingEvent(registry, module));
    }

    public int handleProfileBinding(byte[] name, int key, int state) {
        if (state == 0) {
            return 0;
        }
        int status = configuration.loadProfile(name);
        if (status == 0) {
            registry.post(EventTypes.PROFILE, new Events.ProfileEvent(name, 0));
            return 1;
        }
        ProfileRegistry.Entry profile = profileRegistry.find(name);
        if (profile != null) {
            profile.bindCode = 0;
        }
        registry.post(EventTypes.ERROR, new Events.ErrorEvent(status, "Failed to load profile"));
        profileKeys.remove(key, null);
        return 0;
    }

    public void changeProfileBinding(ProfileRegistry.Entry profile, int key) {
        profileKeys.remove(profile.bindCode, null);
        Object bound = profileKeys.find(key, null);
        if (bound != null) {
            ProfileRegistry.Entry previous = profileRegistry.find((byte[]) bound);
            if (previous != null) {
                previous.bindCode = 0;
                profileKeys.remove(key, null);
            }
        }
        if (key != 0) {
            profileKeys.insert(key, profile.name.clone());
        }
        profile.bindCode = key;
    }

    public int handleModuleBinding(Module module, int state) {
        if (module.holdToActivate()) {
            registry.queueModuleChange(module, state);
            module.setHeld((state) != 0);
        } else if (state != 0) {
            registry.queueModuleChange(module, (module.isEnabled() ? 0 : 1));
        }
        return 1;
    }

    private static void firstCallInitialize(ClientRuntime runtime) {
        SecureRandom entropy = new SecureRandom();
        int w0;
        int w1;
        int w2;
        int w3;
        do {
            w0 = entropy.nextInt();
            w1 = entropy.nextInt();
            w2 = entropy.nextInt();
            w3 = entropy.nextInt();
        } while ((w0 | w1 | w2 | w3) == 0);
        runtime.random.seed(w0, w1, w2, w3);

        runtime.surface = UIDrawing.create();
        runtime.configuration.loadGlobalFile();
    }

    public int handleClientInput(ClientInputEvent event) {
        return ClientInput.handleClientInput(event, keys,
                new ClientInput.Flags() {
                    @Override
                    public int guiEnabled() {
                        return ClientRuntime.this.guiEnabled();
                    }

                    @Override
                    public int friendsEnabled() {
                        return (module(ModuleId.FRIENDS).isEnabled() ? 1 : 0);
                    }
                },
                new ClientInput.Bindings() {
                    @Override
                    public KeyBindings profileKeys() {
                        return profileKeys;
                    }

                    @Override
                    public KeyBindings moduleKeys() {
                        return moduleKeys;
                    }
                },
                new ClientInput.Runtime() {
                    @Override
                    public boolean gameFocused() {
                        return Minecraft.getInstance().focused;
                    }

                    @Override
                    public int uiInput(ClientInputEvent input, int unchanged) {
                        return ClientInput.routeGuiInput(input, unchanged, registry,
                                module(ModuleId.GUI),
                                (gui.allowsInput() ? 1 : 0), guiInput);
                    }

                    @Override
                    public void friendClick() {
                        friends.middleClick(registry);
                    }
                });
    }

    public ClientGameContext context() {
        Minecraft game = Minecraft.getInstance();
        ClientGameContext context = new ClientGameContext(registry, profiles, game, game.world, game.player, keys,
                targetCache);
        if (guiEnabled() == 0) {
            context.focused = game.focused ? 1 : 0;
        }
        return context;
    }

    public void acquire() {
        while (!lock.compareAndSet(0, 1)) {
        }
    }

    public void release() {
        lock.set(0);
    }
}
