package com.ebicep.chatplus.features.moderation;

import com.ebicep.chatplus.config.Config;
import com.ebicep.chatplus.config.ConfigDirectory;
import com.ebicep.chatplus.config.ConfigVariables;
import com.ebicep.chatplus.features.moderation.command.CommandQueue;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.ui.Screens;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Entry point for the moderation tools. Platform code calls the {@code on...} methods. Settings live in ChatPlus's
 * own config; only the server profile is a separate file. Client thread only.
 */
public final class ModTools {
    private static final int MAX_QUEUED = 10;

    private static Engine engine;
    private static Supplier<Screen> pendingScreen;

    private ModTools() {
    }

    public static Path dir() {
        return ConfigDirectory.getConfigDirectory().resolve("chatplus").resolve("moderation");
    }

    private static ConfigVariables settings() {
        return Config.INSTANCE.getValues();
    }

    public static Engine engine() {
        if (engine == null) {
            engine = new Engine(dir(), ModTools::sendCommand, System::currentTimeMillis, new CommandQueue.Limits() {
                @Override
                public long minGapMs() {
                    return settings().getModerationCommandDelayMs();
                }

                @Override
                public long captureWindowMs() {
                    return settings().getModerationLookupWaitMs();
                }

                @Override
                public int maxQueued() {
                    return MAX_QUEUED;
                }
            });
        }
        return engine;
    }

    /** True when the tools are switched on in settings and this server is in the profile. */
    public static boolean active() {
        return settings().getModerationEnabled() && engine().isActive();
    }

    /** The only call that sends anything to a server. Reached only through the engine's queue. */
    private static boolean sendCommand(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        connection.sendCommand(command);
        return true;
    }

    /** Opens a screen on the next tick, which is safe from inside a command. */
    public static void requestOpen(Supplier<Screen> screen) {
        pendingScreen = screen;
    }

    /** Opens the lookup popup for a player and asks the server for their record. */
    public static void openLookup(String player) {
        requestOpen(() -> Screens.lookup(null, player, true));
    }

    // ---- Called by platform code ----

    public static void onTick() {
        engine().tick();
        Minecraft mc = Minecraft.getInstance();
        if (pendingScreen != null && mc.player != null) {
            Supplier<Screen> next = pendingScreen;
            pendingScreen = null;
            mc.setScreen(next.get());
        }
    }

    public static void onLogin() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        engine().setServer(server != null ? server.ip : null);
    }

    public static void onLogout() {
        engine().onDisconnect();
    }

    /** A received chat line. Never cancels or changes the message. */
    public static void onChat(Component message) {
        if (settings().getModerationEnabled()) {
            engine().onLine(new CapturedLine(Instant.now(), message.getString()));
        }
    }

    /** Reloads the server profile and says how it went. */
    public static String reload() {
        Engine e = engine();
        e.reloadConfig();
        return e.errors().isEmpty() ? "Moderation profile reloaded." : "Moderation profile reloaded with problems: " + String.join("; ", e.errors());
    }

    /** One line about the profile and whether the tools are active, for settings and help. */
    public static String status() {
        Engine e = engine();
        if (!settings().getModerationEnabled()) {
            return "Moderation tools are turned off.";
        }
        String profile = e.profile().isEmpty() ? "No server profile installed" : "Profile for " + String.join(", ", e.profile().servers);
        return profile + ". " + (e.isActive() ? "Active on this server." : "Inactive: " + e.inactiveReason() + ".");
    }

    /** Values for a command about one player. */
    public static Map<String, String> playerValues(String player) {
        return Map.of("player", player);
    }
}
