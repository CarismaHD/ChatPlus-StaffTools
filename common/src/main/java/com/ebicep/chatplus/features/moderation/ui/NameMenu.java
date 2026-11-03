package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.core.Engine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

/**
 * The small menu for a player's name right-clicked in chat or picked in the player list. It is drawn like a vanilla
 * tooltip on top of the open screen, which stays open underneath, so chat keeps its full height and tabs. It opens
 * just above the clicked line, starting at the click, so that line stays readable. In chat, the username comes from
 * the server's own click data on the name. Opening it sends nothing.
 */
public final class NameMenu {
    private static final int ROW_H = 12;
    private static final int PAD = 4;
    /** Space between the menu and the clicked line. */
    private static final int GAP = 6;
    private static final int MARGIN = 4;
    /** Commands offered for every player, in this order, when the profile has them. */
    private static final List<String> STANDARD = List.of("msg", "note_add", "warn_add", "tempban", "unstuck");
    private static final List<String> PUNISHING = List.of("warn_add", "tempban");

    private static NameMenu open;

    private record Entry(Component label, Runnable action) {
    }

    private final Screen parent;
    private final String player;
    private final List<Entry> entries = new ArrayList<>();
    private final int boxX;
    private final int boxY;
    private final int boxW;
    private final int boxH;

    private NameMenu(Screen parent, String player, double anchorX, double anchorY, int lineHeight) {
        this.parent = parent;
        this.player = player;
        Minecraft mc = Minecraft.getInstance();
        Engine engine = ModTools.engine();
        if (engine.hasCommand("lookup")) {
            entries.add(new Entry(Component.literal("Lookup"), () -> mc.setScreen(Screens.lookup(parent, player, true))));
        }
        List<String> ids = new ArrayList<>(STANDARD);
        for (CommandTemplate extra : engine.menuCommands()) {
            if (!ids.contains(extra.id()) && !"lookup".equals(extra.id())) {
                ids.add(extra.id());
            }
        }
        for (String id : ids) {
            if (!engine.hasCommand(id)) {
                continue;
            }
            boolean opensPopup = ActionScreen.needsPopup(engine, id);
            Component label = Component.literal(engine.label(id) + (opensPopup ? "..." : ""));
            if (PUNISHING.contains(id)) {
                label = label.copy().withStyle(ChatFormatting.RED);
            }
            entries.add(new Entry(label, () -> run(engine, id, opensPopup)));
        }
        entries.add(new Entry(Component.literal("Copy name").withStyle(ChatFormatting.GRAY), () -> mc.keyboardHandler.setClipboard(player)));

        Font font = mc.font;
        int w = Math.max(font.width(player) + PAD * 2, 90);
        for (Entry e : entries) {
            w = Math.max(w, font.width(e.label()) + PAD * 2);
        }
        boxW = w;
        boxH = PAD + 11 + 3 + entries.size() * ROW_H + PAD - 2;
        // Just above the clicked line, starting at the click. Below the line when there is no room above. The click can
        // be anywhere in the line, so a full line height is kept clear on that side.
        boxX = clamp((int) anchorX, MARGIN, parent.width - boxW - MARGIN);
        int above = (int) anchorY - lineHeight - GAP - boxH;
        boxY = above >= MARGIN ? above : clamp((int) anchorY + lineHeight + GAP, MARGIN, parent.height - boxH - MARGIN);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Opens the menu over the given screen (chat or the player list), at the clicked name, replacing any menu already
     * open. lineHeight is the height of a line on that screen.
     */
    public static void show(Screen parent, String player, double mouseX, double mouseY, int lineHeight) {
        open = new NameMenu(parent, player, mouseX, mouseY, lineHeight);
    }

    /** The middle of a row of the open menu, for clicking it from a test. Row 0 is the first command. */
    public static double[] rowCenter(int row) {
        NameMenu menu = open;
        if (menu == null) {
            return null;
        }
        return new double[]{menu.boxX + menu.boxW / 2.0, menu.boxY + PAD + 11 + 3 + row * ROW_H + ROW_H / 2.0 - 1};
    }

    /** True when the point is inside the open menu. */
    public static boolean covers(double x, double y) {
        NameMenu menu = open;
        return menu != null && x >= menu.boxX && x < menu.boxX + menu.boxW && y >= menu.boxY && y < menu.boxY + menu.boxH;
    }

    public static boolean isOpen() {
        return open != null;
    }

    public static void close() {
        open = null;
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY) {
        if (open != null) {
            open.draw(g, mouseX, mouseY);
        }
    }

    /**
     * A click while the menu is open. A left-click on a row runs it, and any click outside closes the menu. Every click
     * is used up by the menu, so it never also selects or drags chat.
     */
    public static void click(double mouseX, double mouseY, int button) {
        NameMenu menu = open;
        if (menu == null) {
            return;
        }
        int y = menu.boxY + PAD + 11 + 3;
        for (Entry e : menu.entries) {
            if (mouseX >= menu.boxX && mouseX < menu.boxX + menu.boxW && mouseY >= y - 1 && mouseY < y - 1 + ROW_H) {
                if (button == 0) {
                    open = null;
                    e.action().run();
                }
                return;
            }
            y += ROW_H;
        }
        open = null;
    }

    private void run(Engine engine, String id, boolean opensPopup) {
        if (opensPopup) {
            Minecraft.getInstance().setScreen(new ActionScreen(parent, id, player, Map.of()));
            return;
        }
        Engine.Submission s = engine.submit(id, ModTools.playerValues(player), null);
        if (!s.queued()) {
            ChatPlus.INSTANCE.sendMessage(Component.literal("Not sent: " + String.join(", ", s.problems())).withStyle(ChatFormatting.RED));
        }
    }

    private void draw(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        TooltipRenderUtil.renderTooltipBackground(g, boxX, boxY, boxW, boxH, 0);
        g.drawString(font, player, boxX + PAD, boxY + PAD, 0xFFFFFF55);
        int y = boxY + PAD + 11 + 3;
        for (Entry e : entries) {
            boolean over = mouseX >= boxX && mouseX < boxX + boxW && mouseY >= y - 1 && mouseY < y - 1 + ROW_H;
            if (over) {
                g.fill(boxX, y - 2, boxX + boxW, y - 2 + ROW_H, 0x40FFFFFF);
            }
            g.drawString(font, e.label(), boxX + PAD, y, 0xFFFFFFFF);
            y += ROW_H;
        }
        g.pose().popPose();
    }
}
