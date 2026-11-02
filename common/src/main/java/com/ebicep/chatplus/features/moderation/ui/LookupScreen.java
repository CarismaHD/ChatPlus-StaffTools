package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.command.Response;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.parse.LookupResult;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A player's server record from the lookup command, split into warnings, notes and bans. Uses only vanilla widgets.
 * Each entry can be removed, which asks first.
 */
final class LookupScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int LINE_H = 10;

    /** One block of text in the list, with an optional Remove button. */
    private record Row(List<FormattedCharSequence> lines, int color, Button remove) {
        int height() {
            return lines.size() * LINE_H + 4;
        }
    }

    private final Screen parent;
    private final String player;
    private final boolean requestOnOpen;
    private final List<Row> rows = new ArrayList<>();
    private boolean waiting;
    private boolean noReply;
    private double scroll;
    private int top;
    private int bottom;
    private int left;
    private int listW;

    LookupScreen(Screen parent, String player, boolean requestOnOpen) {
        super(Component.literal("Lookup"));
        this.parent = parent;
        this.player = player;
        this.requestOnOpen = requestOnOpen;
    }

    @Override
    protected void init() {
        listW = Math.min(360, width - 40);
        left = (width - listW) / 2;
        top = 44;
        bottom = height - 34;
        if (requestOnOpen && !waiting && ModTools.engine().lookupTime(player) == 0 && !noReply) {
            request();
        }
        buildRows();
        int buttonW = 100;
        int x = width / 2 - buttonW - 2;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> request()).bounds(x, height - 26, buttonW, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 2, height - 26, buttonW, 20).build());
    }

    private void request() {
        Engine.Submission s = ModTools.engine().submit("lookup", ModTools.playerValues(player), this::onResponse);
        waiting = s.queued();
        noReply = false;
        if (!s.queued()) {
            ChatPlus.INSTANCE.sendMessage(Component.literal("Lookup not sent: " + String.join(", ", s.problems())).withStyle(ChatFormatting.RED));
        }
    }

    private void onResponse(Response response) {
        waiting = false;
        noReply = !(response.parsed() instanceof LookupResult);
        if (minecraft != null && minecraft.screen == this) {
            rebuildWidgets();
        }
    }

    private void buildRows() {
        rows.clear();
        LookupResult l = ModTools.engine().lookup(player);
        if (l == null) {
            return;
        }
        section("Warnings", l.warningEntries(), "warn_remove", "warningId", ChatFormatting.GOLD);
        section("Notes", l.noteEntries(), "note_remove", "noteId", ChatFormatting.AQUA);
        if (l.activeBans() > 0) {
            Button unban = null;
            if (ModTools.engine().hasCommand("unban")) {
                unban = addRenderableWidget(Button.builder(Component.literal(ModTools.engine().label("unban") + "..."),
                        b -> minecraft.setScreen(new ActionScreen(this, "unban", player, Map.of()))).bounds(0, 0, 70, 16).build());
            }
            rows.add(new Row(wrap(Component.literal("Active bans: " + l.activeBans() + ".")
                    .withStyle(ChatFormatting.RED)), 0xFFFFFF, unban));
        }
        l.rawSections().forEach((header, lines) -> {
            rows.add(new Row(wrap(Component.literal(header).withStyle(ChatFormatting.GRAY)), 0xFFFFFF, null));
            lines.forEach(line -> rows.add(new Row(wrap(Component.literal(line)), 0xFFFFFF, null)));
        });
    }

    private void section(String title, List<LookupResult.Entry> entries, String removeTemplate, String idKey, ChatFormatting color) {
        rows.add(new Row(wrap(Component.literal(title + " (" + entries.size() + ")").withStyle(color, ChatFormatting.BOLD)), 0xFFFFFF, null));
        if (entries.isEmpty()) {
            rows.add(new Row(wrap(Component.literal("None").withStyle(ChatFormatting.DARK_GRAY)), 0xFFFFFF, null));
        }
        boolean canRemove = ModTools.engine().hasCommand(removeTemplate);
        for (LookupResult.Entry e : entries) {
            String head = "#" + e.id() + (e.status() != null ? "  " + e.status().toLowerCase() : "") + "  " + e.timestamp() + "  by " + e.staff();
            List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(head).withStyle(ChatFormatting.GRAY), listW - 60));
            lines.addAll(font.split(Component.literal(e.text()), listW - 8));
            Button remove = null;
            if (canRemove) {
                String id = String.valueOf(e.id());
                remove = addRenderableWidget(Button.builder(Component.literal("Remove"),
                        b -> minecraft.setScreen(new ActionScreen(this, removeTemplate, player, Map.of(idKey, id)))).bounds(0, 0, 50, 16).build());
            }
            rows.add(new Row(lines, 0xFFFFFF, remove));
        }
    }

    private List<FormattedCharSequence> wrap(Component c) {
        return font.split(c, listW - 8);
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows) {
            h += r.height();
        }
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Place each Remove button beside its entry, and hide the ones scrolled out of view.
        int y = top - (int) scroll;
        for (Row r : rows) {
            if (r.remove() != null) {
                r.remove().setX(left + listW - r.remove().getWidth());
                r.remove().setY(y);
                r.remove().visible = y >= top && y + r.remove().getHeight() <= bottom;
            }
            y += r.height();
        }
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, Component.literal("Lookup: ").append(Component.literal(player).withStyle(ChatFormatting.YELLOW)), width / 2, 12, 0xFFFFFF);
        LookupResult l = ModTools.engine().lookup(player);
        Component summary;
        if (waiting) {
            summary = Component.literal("Waiting for the server...").withStyle(ChatFormatting.GRAY);
        } else if (noReply && l == null) {
            summary = Component.literal("No lookup reply from the server. Check chat for an error.").withStyle(ChatFormatting.RED);
        } else if (l == null) {
            summary = Component.literal("Click Refresh to look this player up.").withStyle(ChatFormatting.GRAY);
        } else {
            summary = Component.literal("Warnings " + l.warnings() + "   Notes " + l.notes() + "   ")
                    .append(Component.literal("Active bans " + l.activeBans()).withStyle(l.activeBans() > 0 ? ChatFormatting.RED : ChatFormatting.WHITE))
                    .append(Component.literal("   at " + TIME.format(Instant.ofEpochMilli(ModTools.engine().lookupTime(player)))).withStyle(ChatFormatting.DARK_GRAY));
        }
        g.drawCenteredString(font, summary, width / 2, 26, 0xFFFFFF);

        g.enableScissor(left, top, left + listW, bottom);
        y = top - (int) scroll;
        for (Row r : rows) {
            int ly = y + 2;
            for (FormattedCharSequence line : r.lines()) {
                g.drawString(font, line, left, ly, r.color());
                ly += LINE_H;
            }
            y += r.height();
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight() - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll - scrollY * 12));
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
