package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Everyone seen online on this server, searchable by username or nickname, so a player can be picked even when they
 * aren't in chat. Clicking a name opens the same menu as right-clicking it in chat. Offline players have a Seen button,
 * which asks the server when they were last online and updates the list. Uses only vanilla widgets.
 */
final class PlayerListScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int SEEN_W = 32;

    private final Screen parent;
    private EditBox search;
    private List<PlayerDirectory.Player> shown = List.of();
    private String lastQuery;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int left;
    private int columnW;
    /** One Seen button per visible row, moved to whichever offline player is in that row. */
    private final List<Button> seenButtons = new ArrayList<>();
    private final List<PlayerDirectory.Player> rowPlayers = new ArrayList<>();
    /** Players whose /seen is on its way, and the last reply, shown under the list. */
    private final Map<UUID, Boolean> asking = new HashMap<>();
    private String lastReply;

    PlayerListScreen(Screen parent) {
        super(Component.literal("Players"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        columnW = Math.min(320, width - 40);
        left = (width - columnW) / 2;
        String query = search != null ? search.getValue() : "";
        search = new EditBox(font, left, 30, columnW, 20, Component.literal("Search"));
        search.setHint(Component.literal("Search players or nicknames").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        addRenderableWidget(search);
        setInitialFocus(search);
        listTop = 56;
        listBottom = height - 46;
        seenButtons.clear();
        rowPlayers.clear();
        for (int i = 0; i < visibleRows(); i++) {
            int row = i;
            Button b = addRenderableWidget(Button.builder(Component.literal("Seen"), btn -> askSeen(row)).bounds(0, 0, SEEN_W, ROW_H - 2).build());
            b.visible = false;
            seenButtons.add(b);
            rowPlayers.add(null);
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
        lastQuery = null;
    }

    private void refresh() {
        String query = search.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scroll = 0;
        }
        shown = ModTools.players().search(query);
        scroll = Math.max(0, Math.min(scroll, shown.size() - visibleRows()));
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    /** Sends /seen for the player in a row, on this click only, and updates their last-seen time from the reply. */
    private void askSeen(int row) {
        PlayerDirectory.Player p = rowPlayers.get(row);
        if (p == null) {
            return;
        }
        asking.put(p.id(), Boolean.TRUE);
        lastReply = null;
        Engine.Submission s = ModTools.engine().submit(ModTools.SEEN, ModTools.playerValues(p.name()), response -> {
            asking.remove(p.id());
            List<String> lines = response.parsed() instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            ModTools.applySeenReply(p.name(), lines);
            lastReply = lines.isEmpty() ? p.name() + ": no reply from the server. Check chat." : String.join(" ", lines);
        });
        if (!s.queued()) {
            asking.remove(p.id());
            lastReply = "Not sent: " + String.join(", ", s.problems());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        PlayerDirectory dir = ModTools.players();
        boolean canAskSeen = ModTools.engine().hasCommand(ModTools.SEEN);
        // Place the Seen buttons before drawing, so they show in the right rows this frame.
        for (int i = 0; i < seenButtons.size(); i++) {
            int index = scroll + i;
            PlayerDirectory.Player p = index < shown.size() ? shown.get(index) : null;
            rowPlayers.set(i, p);
            Button b = seenButtons.get(i);
            b.visible = p != null && canAskSeen && !dir.isOnline(p.id()) && !NameMenu.isOpen();
            b.active = p != null && !asking.containsKey(p.id());
            b.setX(left + columnW - 4 - SEEN_W);
            b.setY(listTop + i * ROW_H + 1);
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.literal("Players: ")
                .append(Component.literal(dir.onlineCount() + " online").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(", " + dir.size() + " seen").withStyle(ChatFormatting.GRAY)), width / 2, 14, 0xFFFFFF);
        if (!ModTools.active()) {
            g.drawCenteredString(font, Component.literal(ModTools.status()).withStyle(ChatFormatting.RED), width / 2, listTop + 4, 0xFFFFFF);
        } else if (shown.isEmpty()) {
            String text = dir.size() == 0 ? "Nobody seen yet. Players are added as you see them online on this server." : "No player matches.";
            g.drawCenteredString(font, Component.literal(text).withStyle(ChatFormatting.GRAY), width / 2, listTop + 4, 0xFFFFFF);
        }
        g.enableScissor(left, listTop, left + columnW, listBottom);
        long now = System.currentTimeMillis();
        for (int i = 0; i < seenButtons.size(); i++) {
            PlayerDirectory.Player p = rowPlayers.get(i);
            if (p == null) {
                break;
            }
            int y = listTop + i * ROW_H;
            boolean online = dir.isOnline(p.id());
            int statusRight = left + columnW - 4 - (seenButtons.get(i).visible ? SEEN_W + 4 : 0);
            boolean over = !NameMenu.isOpen() && mouseX >= left && mouseX < statusRight && mouseY >= y && mouseY < y + ROW_H;
            if (over) {
                g.fill(left, y, statusRight, y + ROW_H, 0x30FFFFFF);
            }
            Component status = online ? Component.literal("online").withStyle(ChatFormatting.GREEN)
                    : asking.containsKey(p.id()) ? Component.literal("asking...").withStyle(ChatFormatting.GRAY)
                    : Component.literal("seen " + ago(now - p.lastSeen())).withStyle(ChatFormatting.GRAY);
            int statusX = statusRight - font.width(status);
            g.drawString(font, status, statusX, y + 3, 0xFFFFFFFF);
            g.drawString(font, p.name(), left + 4, y + 3, online ? 0xFFFFFFFF : 0xFFAAAAAA);
            if (p.nick() != null) {
                int nickX = left + 4 + font.width(p.name()) + 6;
                String nick = font.plainSubstrByWidth(p.nick(), Math.max(0, statusX - 6 - nickX));
                g.drawString(font, nick, nickX, y + 3, 0xFF777777);
            }
        }
        g.disableScissor();
        if (lastReply != null) {
            String line = font.plainSubstrByWidth(lastReply, columnW);
            g.drawString(font, line, left, listBottom + 4, 0xFFAAAAAA);
        }
        NameMenu.render(g, mouseX, mouseY);
    }

    /** A rough age such as "5m ago" or "3d ago". */
    static String ago(long ms) {
        long minutes = Math.max(0, ms) / 60_000;
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        if (days < 60) {
            return days + "d ago";
        }
        return days < 730 ? days / 30 + "mo ago" : days / 365 + "y ago";
    }

    /** The player in the row under the point, or null. */
    private PlayerDirectory.Player rowAt(double mouseX, double mouseY) {
        if (mouseX < left || mouseX >= left + columnW || mouseY < listTop || mouseY >= listBottom) {
            return null;
        }
        int index = scroll + (int) ((mouseY - listTop) / ROW_H);
        return index < shown.size() ? shown.get(index) : null;
    }

    /** The middle of the row for a player, for clicking it from a test. Null when it isn't on screen. */
    double[] rowCenter(String name) {
        refresh();
        for (int i = scroll; i < shown.size() && i - scroll < visibleRows(); i++) {
            if (shown.get(i).name().equalsIgnoreCase(name)) {
                return new double[]{left + 20, listTop + (i - scroll) * ROW_H + ROW_H / 2.0};
            }
        }
        return null;
    }

    /** For tests: presses the Seen button in a player's row, if it has one. */
    boolean pressSeen(String name) {
        for (int i = 0; i < rowPlayers.size(); i++) {
            PlayerDirectory.Player p = rowPlayers.get(i);
            if (p != null && p.name().equalsIgnoreCase(name) && seenButtons.get(i).visible) {
                seenButtons.get(i).onPress();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (NameMenu.isOpen()) {
            NameMenu.click(mouseX, mouseY, button);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        PlayerDirectory.Player p = rowAt(mouseX, mouseY);
        if (p != null && (button == 0 || button == 1)) {
            NameMenu.show(this, p.name(), mouseX, mouseY, ROW_H);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        NameMenu.close();
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY) * 3, shown.size() - visibleRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && NameMenu.isOpen()) {
            NameMenu.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        NameMenu.close();
        ModTools.savePlayers();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
