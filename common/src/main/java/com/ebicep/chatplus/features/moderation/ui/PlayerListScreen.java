package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Everyone seen online on this server, searchable, so a player can be picked even when they aren't in chat. Clicking
 * a name opens the same menu as right-clicking it in chat. Uses only vanilla widgets.
 */
final class PlayerListScreen extends Screen {
    private static final int ROW_H = 12;

    private final Screen parent;
    private EditBox search;
    private List<PlayerDirectory.Player> shown = List.of();
    private String lastQuery;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int left;
    private int columnW;

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
        search.setHint(Component.literal("Search players").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        addRenderableWidget(search);
        setInitialFocus(search);
        listTop = 56;
        listBottom = height - 34;
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        super.render(g, mouseX, mouseY, partialTick);
        PlayerDirectory dir = ModTools.players();
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
        int y = listTop;
        for (int i = scroll; i < shown.size() && y < listBottom; i++) {
            PlayerDirectory.Player p = shown.get(i);
            boolean online = dir.isOnline(p.id());
            boolean over = !NameMenu.isOpen() && mouseX >= left && mouseX < left + columnW && mouseY >= y && mouseY < y + ROW_H;
            if (over) {
                g.fill(left, y, left + columnW, y + ROW_H, 0x30FFFFFF);
            }
            g.drawString(font, p.name(), left + 4, y + 2, online ? 0xFFFFFFFF : 0xFFAAAAAA);
            Component status = online ? Component.literal("online").withStyle(ChatFormatting.GREEN)
                    : Component.literal("seen " + ago(now - p.lastSeen())).withStyle(ChatFormatting.GRAY);
            g.drawString(font, status, left + columnW - 4 - font.width(status), y + 2, 0xFFFFFFFF);
            y += ROW_H;
        }
        g.disableScissor();
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (NameMenu.isOpen()) {
            NameMenu.click(mouseX, mouseY, button);
            return true;
        }
        PlayerDirectory.Player p = rowAt(mouseX, mouseY);
        if (p != null && (button == 0 || button == 1)) {
            NameMenu.show(this, p.name(), mouseX, mouseY, ROW_H);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
