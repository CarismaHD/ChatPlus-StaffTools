package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.core.Engine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

/**
 * The small menu for a player's name right-clicked in chat. It is drawn like a vanilla tooltip over the chat, which
 * stays visible behind it. The username comes from the server's own click data on the name. Opening it sends nothing.
 */
final class NameMenuScreen extends Screen {
    private static final int ROW_H = 12;
    private static final int PAD = 4;
    /** Commands offered for every player, in this order, when the profile has them. */
    private static final List<String> STANDARD = List.of("msg", "note_add", "warn_add", "tempban", "unstuck");
    private static final List<String> PUNISHING = List.of("warn_add", "tempban");

    private record Entry(Component label, Runnable action) {
    }

    private final Screen parent;
    private final String player;
    private final double anchorX;
    private final double anchorY;
    private final List<Entry> entries = new ArrayList<>();
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    NameMenuScreen(Screen parent, String player, double anchorX, double anchorY) {
        super(Component.literal(player));
        this.parent = parent;
        this.player = player;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override
    protected void init() {
        Engine engine = ModTools.engine();
        entries.clear();
        if (engine.hasCommand("lookup")) {
            entries.add(new Entry(Component.literal("Lookup"), () -> minecraft.setScreen(Screens.lookup(parent, player, true))));
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
        entries.add(new Entry(Component.literal("Copy name").withStyle(ChatFormatting.GRAY), () -> {
            minecraft.keyboardHandler.setClipboard(player);
            minecraft.setScreen(parent);
        }));

        boxW = font.width(player) + PAD * 2;
        for (Entry e : entries) {
            boxW = Math.max(boxW, font.width(e.label()) + PAD * 2);
        }
        boxW = Math.max(boxW, 90);
        boxH = PAD + 11 + 3 + entries.size() * ROW_H + PAD - 2;
        boxX = (int) Math.min(anchorX + 4, width - boxW - 6);
        boxY = (int) Math.max(6, Math.min(anchorY - 4, height - boxH - 6));
    }

    private void run(Engine engine, String id, boolean opensPopup) {
        if (opensPopup) {
            minecraft.setScreen(new ActionScreen(parent, id, player, Map.of()));
            return;
        }
        minecraft.setScreen(parent);
        Engine.Submission s = engine.submit(id, ModTools.playerValues(player), null);
        if (!s.queued()) {
            ChatPlus.INSTANCE.sendMessage(Component.literal("Not sent: " + String.join(", ", s.problems())).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The chat screen behind stays visible, so the menu feels attached to the line that was clicked.
        if (parent != null) {
            parent.render(g, -1, -1, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = boxY + PAD + 11 + 3;
        for (Entry e : entries) {
            if (mouseX >= boxX && mouseX < boxX + boxW && mouseY >= y - 1 && mouseY < y - 1 + ROW_H) {
                if (button == 0) {
                    e.action().run();
                }
                return true;
            }
            y += ROW_H;
        }
        onClose();
        return true;
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
