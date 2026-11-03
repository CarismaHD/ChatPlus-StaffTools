package com.ebicep.chatplus.features.moderation;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.config.Config;
import com.ebicep.chatplus.config.ConfigDirectory;
import com.ebicep.chatplus.config.ConfigVariables;
import com.ebicep.chatplus.features.chattabs.ChatTab;
import com.ebicep.chatplus.features.chatwindows.ChatWindow;
import com.ebicep.chatplus.features.moderation.command.CommandQueue;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.core.Nicknames;
import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import com.ebicep.chatplus.features.moderation.core.SeenTime;
import com.ebicep.chatplus.features.moderation.core.SlashGuard;
import com.ebicep.chatplus.features.moderation.parse.CapturedLine;
import com.ebicep.chatplus.features.moderation.ui.Screens;
import com.ebicep.chatplus.hud.ChatManager;
import com.ebicep.chatplus.mixin.IMixinChatScreen;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Entry point for the moderation tools. Platform code calls the {@code on...} methods. Settings live in ChatPlus's
 * own config; the server profile and the player list are separate files. Client thread only.
 */
public final class ModTools {
    private static final int MAX_QUEUED = 10;
    /** How often the tab list is read into the player list. */
    private static final int PLAYER_SCAN_TICKS = 20;
    /** How often a changed player list is written to disk. */
    private static final int PLAYER_SAVE_TICKS = 20 * 60 * 5;
    /** How long a held-back message can be sent as chat by pressing Enter again. */
    private static final long HOLD_RESEND_MS = 10_000;
    /** The profile command that asks the server when a player was last online. */
    public static final String SEEN = "seen";

    private static Engine engine;
    private static Supplier<Screen> pendingScreen;
    private static final PlayerDirectory players = new PlayerDirectory();
    private static Path playersFile;
    private static int ticks;
    private static String heldText;
    private static long heldAt;

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

    /** Everyone seen online on this server. Empty when the tools aren't active here. */
    public static PlayerDirectory players() {
        return players;
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

    /** Opens the player list. */
    public static void openPlayers() {
        requestOpen(() -> Screens.players(null));
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
        ticks++;
        if (ticks % PLAYER_SCAN_TICKS == 0) {
            scanPlayers();
        }
        if (ticks % PLAYER_SAVE_TICKS == 0) {
            savePlayers();
        }
    }

    public static void onLogin() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        startSession(server != null ? server.ip : null);
    }

    /** Starts using a server: its profile entry, and the player list saved for it. */
    public static void startSession(String address) {
        savePlayers();
        engine().setServer(address);
        playersFile = null;
        players.clear();
        try {
            if (active()) {
                playersFile = dir().resolve("players").resolve(PlayerDirectory.fileNameFor(address));
                players.load(playersFile);
            }
        } catch (IOException e) {
            ChatPlus.INSTANCE.getLOGGER().warn("Player list could not be read: {}", e.getMessage());
        }
        ticks = 0;
        scanPlayers();
    }

    public static void onLogout() {
        savePlayers();
        players.clearOnline();
        playersFile = null;
        engine().onDisconnect();
    }

    /** Writes the player list now, if anything changed. */
    public static void savePlayers() {
        if (playersFile == null || !players.dirty()) {
            return;
        }
        try {
            players.save(playersFile);
        } catch (IOException e) {
            ChatPlus.INSTANCE.getLOGGER().warn("Player list could not be saved: {}", e.getMessage());
        }
    }

    /** Reads who is online from the tab list. */
    private static void scanPlayers() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (playersFile == null || connection == null || !active()) {
            return;
        }
        Map<UUID, String> online = new HashMap<>();
        Map<UUID, String> nicks = new HashMap<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (name != null && !name.isEmpty()) {
                online.put(info.getProfile().getId(), name);
                Component tabName = info.getTabListDisplayName();
                String nick = Nicknames.fromTabName(tabName != null ? tabName.getString() : null, name, engine().profile().nickFromTab);
                if (nick != null) {
                    nicks.put(info.getProfile().getId(), nick);
                }
            }
        }
        players.update(online, nicks, System.currentTimeMillis());
    }

    /**
     * Applies a reply to the profile's {@code seen} command: its {@code seen} group, a date and time, becomes the
     * player's last-seen time in the player list. Returns true when it changed anything.
     */
    public static boolean applySeenReply(String player, List<String> replyLines) {
        Long when = SeenTime.parse(engine().replyGroup(SEEN, replyLines, "seen"));
        return when != null && players.markSeen(player, when);
    }

    /** A received chat line. Never cancels or changes the message. */
    public static void onChat(Component message) {
        if (settings().getModerationEnabled()) {
            engine().onLine(new CapturedLine(Instant.now(), message.getString()));
        }
    }

    /**
     * Called when Enter is pressed in chat. Returns true to hold the message back because it looks like a command typed
     * without its slash. Pressing Enter again on the same text soon after sends it as chat.
     */
    public static boolean holdBackMessage(ChatScreen screen) {
        if (!settings().getModerationSlashGuard()) {
            return false;
        }
        String typed = ((IMixinChatScreen) screen).getInput().getValue();
        // What would really be sent: a tab's auto-prefix such as "/msg Steve " turns the text into a command.
        ChatTab tab = ChatManager.INSTANCE.getGlobalSelectedTab();
        String text = typed.startsWith("/") && tab.getCommandsOverrideAutoPrefix() ? typed : tab.getAutoPrefix() + typed;
        List<String> guardWords = active() ? engine().profile().slashGuard : List.of();
        if (!SlashGuard.shouldHold(text, guardWords, ModTools::isServerSubcommand)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (text.equals(heldText) && now - heldAt <= HOLD_RESEND_MS) {
            heldText = null;
            return false;
        }
        heldText = text;
        heldAt = now;
        ChatPlus.INSTANCE.sendMessage(Component.literal("Not sent: this looks like a command without its /. Press Enter again to send it as chat.")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    /** True when first is a command this server sent us and second is one of its literal sub-commands. */
    private static boolean isServerSubcommand(String first, String second) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        CommandNode<SharedSuggestionProvider> node = connection.getCommands().getRoot().getChild(first);
        if (node == null) {
            return false;
        }
        if (node.getChildren().isEmpty() && node.getRedirect() != null) {
            node = node.getRedirect();
        }
        return node.getChild(second) instanceof LiteralCommandNode;
    }

    /**
     * A player's most recent messages from the chat already on screen, newest first. A message counts only when it has
     * a name whose click data is this player, the same rule the name menu uses. Nothing is stored.
     */
    public static List<String> recentMessages(String player, int max) {
        List<ChatTab.ChatPlusGuiMessage> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChatWindow window : Config.INSTANCE.getValues().getChatWindows()) {
            for (ChatTab tab : window.getTabSettings().getTabs()) {
                List<ChatTab.ChatPlusGuiMessage> messages = tab.getMessages();
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatTab.ChatPlusGuiMessage m = messages.get(i);
                    // The same line can be in several tabs; it is counted once.
                    String key = m.getGuiMessage().addedTime() + "|" + m.getGuiMessage().content().getString();
                    if (!seen.contains(key) && isFrom(m.getGuiMessage().content(), player)) {
                        seen.add(key);
                        found.add(m);
                    }
                }
            }
        }
        found.sort((a, b) -> Integer.compare(b.getGuiMessage().addedTime(), a.getGuiMessage().addedTime()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(max, found.size()); i++) {
            result.add(found.get(i).getGuiMessage().content().getString());
        }
        return result;
    }

    private static boolean isFrom(Component message, String player) {
        Engine e = engine();
        return message.visit((Style style, String text) -> {
            ClickEvent click = style.getClickEvent();
            if (click != null && player.equalsIgnoreCase(e.nameFromClick(click.getValue()))) {
                return Optional.of(Boolean.TRUE);
            }
            return Optional.<Boolean>empty();
        }, Style.EMPTY).isPresent();
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
