package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.command.TemplateRenderer;
import com.ebicep.chatplus.features.moderation.core.Engine;
import com.ebicep.chatplus.features.moderation.core.NameSimilarity;
import com.ebicep.chatplus.features.moderation.core.PlayerDirectory;
import com.ebicep.chatplus.features.moderation.parse.LookupResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Asks for what a command needs, shows the exact command, and sends only on Send. It is the confirmation for every
 * command that changes something. Uses only vanilla widgets.
 */
final class ActionScreen extends Screen {
    /** Send ignores clicks at first, so a double-click on the menu behind cannot confirm by accident. */
    private static final long SEND_DELAY_MS = 500;
    /** Commands that punish, which get the player's recent messages and name checks shown before sending. */
    private static final List<String> PUNISHING = List.of("warn_add", "tempban");
    /** Commands that go on the player's record, which get where you are, the time and their record so far. */
    private static final List<String> RECORDED = List.of("warn_add", "tempban", "note_add");
    /** The profile command that asks the server who owns the chunk you're in. */
    private static final String CHUNK_OWNER = "chunk_owner";
    /** The profile command that lists a team's owner and members, for a chunk owned by a team. */
    private static final String TEAM_INFO = "team_info";
    private static final int CONTEXT_MESSAGES = 3;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private final String templateId;
    private final String player;
    private final Map<String, String> fixed;
    private final boolean needsText;
    private final boolean needsDuration;
    private final boolean hasSilent;
    private final String silentFlag;
    private final String textLabel;
    private final long openedAt = System.currentTimeMillis();
    private final boolean punishing;
    private final boolean recorded;
    private final boolean offline;
    private final List<String> lookAlikes;
    private final List<String> recent;
    /** Where you were standing when the screen opened, and when. Fixed, so what's inserted matches what's shown. */
    private final String coords;
    private final String location;
    private final String place;
    private final String time;
    private String text = "";
    private String duration = "";
    private boolean silent;
    /** The server's reply to the chunk owner check: null before asking, empty while waiting. */
    private List<String> chunkReply;
    /** The team named in the chunk owner reply, or null, and its ID when the reply gives one. */
    private String team;
    private String teamId;
    /** The server's reply to the team check: null before asking, empty while waiting. */
    private List<String> teamReply;
    private Button chunkButton;
    private EditBox textBox;
    private Button send;
    private int left;
    private int columnW;
    private int top;
    private int previewY;
    private int contextY;

    /** @param fixed values already known, such as the ID of the warning to remove */
    ActionScreen(Screen parent, String templateId, String player, Map<String, String> fixed) {
        super(Component.literal(ModTools.engine().label(templateId)));
        this.parent = parent;
        this.templateId = templateId;
        this.player = player;
        this.fixed = fixed;
        CommandTemplate t = ModTools.engine().profile().command(templateId);
        String template = t != null ? t.template() : "";
        this.needsText = template.contains("{reason}") || template.contains("{message}");
        this.needsDuration = template.contains("{duration}");
        this.silentFlag = ModTools.engine().profile().silentFlag;
        this.hasSilent = template.contains("{silent}") && !silentFlag.isEmpty();
        this.textLabel = template.contains("{reason}") ? "Reason" : "Message";
        // What helps catch a mistake before punishing: what they said, whether someone with a similar name is
        // online, and whether they are online at all. Read once, from what the client already has.
        this.punishing = PUNISHING.contains(templateId);
        this.recorded = RECORDED.contains(templateId);
        PlayerDirectory players = ModTools.players();
        this.offline = punishing && players.knowsWhoIsOnline() && !players.isOnline(player);
        this.lookAlikes = punishing ? NameSimilarity.lookAlikes(player, players.onlineNames()) : List.of();
        this.recent = punishing ? ModTools.recentMessages(player, CONTEXT_MESSAGES) : List.of();
        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null) {
            BlockPos pos = me.blockPosition();
            String dimension = shortId(me.level().dimension().location());
            String biome = me.level().getBiome(pos).unwrapKey().map(k -> shortId(k.location())).orElse("unknown biome");
            this.coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            this.location = coords + " (" + dimension + ")";
            this.place = dimension + ", " + biome;
        } else {
            this.coords = "";
            this.location = "";
            this.place = "";
        }
        this.time = LocalDateTime.now().format(TIME);
    }

    /** "overworld" for Minecraft's own IDs, the full ID for a mod's, such as "examplemod:mining". */
    private static String shortId(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    /** True when a command needs input or confirmation before it can be sent. */
    static boolean needsPopup(Engine engine, String templateId) {
        CommandTemplate t = engine.profile().command(templateId);
        if (t == null) {
            return true;
        }
        String template = t.template();
        return t.confirm() || template.contains("{reason}") || template.contains("{message}") || template.contains("{duration}");
    }

    private Map<String, String> values() {
        Map<String, String> v = new HashMap<>(fixed);
        v.put("player", player);
        v.put("reason", text);
        v.put("message", text);
        v.put("duration", duration);
        v.put("silent", silent ? silentFlag : "");
        return v;
    }

    @Override
    protected void init() {
        columnW = Math.min(300, width - 40);
        left = (width - columnW) / 2;
        // The info lines above the box: where you are, the time, and their record.
        int infoLines = recorded ? 3 : 0;
        top = Math.max(8, height / 2 - 70 - infoLines * 11);
        int y = top + 20 + infoLines * 11;
        if (needsText) {
            textBox = new EditBox(font, left, y, columnW, 20, Component.literal(textLabel));
            textBox.setMaxLength(256);
            textBox.setHint(Component.literal(textLabel).withStyle(ChatFormatting.DARK_GRAY));
            textBox.setValue(text);
            textBox.setResponder(s -> text = s);
            addRenderableWidget(textBox);
            setInitialFocus(textBox);
            y += 24;
            if (recorded) {
                int w = (columnW - 8) / 3;
                Button insertLocation = addRenderableWidget(Button.builder(Component.literal("Insert location"), b -> insert("at " + location))
                        .bounds(left, y, w, 20).build());
                insertLocation.active = !location.isEmpty();
                addRenderableWidget(Button.builder(Component.literal("Insert time"), b -> insert(time)).bounds(left + w + 4, y, w, 20).build());
                if (ModTools.engine().hasCommand(CHUNK_OWNER)) {
                    chunkButton = addRenderableWidget(Button.builder(Component.literal(team != null ? "Team members" : "Chunk owner"), b -> onChunkButton())
                            .bounds(left + 2 * (w + 4), y, w, 20).build());
                }
                y += 24;
            }
        }
        if (needsDuration || hasSilent) {
            if (needsDuration) {
                EditBox d = new EditBox(font, left, y, 100, 20, Component.literal("Duration"));
                d.setHint(Component.literal("Duration, like 1d").withStyle(ChatFormatting.DARK_GRAY));
                d.setValue(duration);
                d.setResponder(s -> duration = s);
                addRenderableWidget(d);
                if (!needsText) {
                    setInitialFocus(d);
                }
            }
            if (hasSilent) {
                addRenderableWidget(Checkbox.builder(Component.literal("Silent"), font).pos(left + 110, y).selected(silent)
                        .onValueChange((c, v) -> silent = v).build());
            }
            y += 24;
        }
        previewY = y + 4;
        int buttonsY = Math.min(height - 28, previewY + 44);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(left, buttonsY, columnW / 2 - 2, 20).build());
        send = addRenderableWidget(Button.builder(Component.literal("Send"), b -> send()).bounds(left + columnW / 2 + 2, buttonsY, columnW / 2 - 2, 20).build());
        send.active = false;
        contextY = buttonsY + 28;
    }

    /** Adds text to the end of the reason, with a space before it when needed. */
    private void insert(String addition) {
        if (textBox == null || addition.isEmpty()) {
            return;
        }
        String current = textBox.getValue();
        String joined = current.isEmpty() || current.endsWith(" ") ? current + addition : current + " " + addition;
        textBox.setValue(joined);
        setFocused(textBox);
    }

    /**
     * The chunk button's first click asks who owns the chunk. When the owner is a team and the profile has a
     * {@code team_info} command, the button becomes "Team members" and the next click asks for the team's members.
     */
    private void onChunkButton() {
        if (team != null && ModTools.engine().hasCommand(TEAM_INFO)) {
            teamReply = List.of();
            Map<String, String> values = new HashMap<>(Map.of("player", player, "team", team));
            if (teamId != null) {
                values.put("teamId", teamId);
            }
            ask(TEAM_INFO, values, lines -> teamReply = lines);
            return;
        }
        chunkReply = List.of();
        ask(CHUNK_OWNER, ModTools.playerValues(player), lines -> {
            chunkReply = lines;
            team = ModTools.engine().replyGroup(CHUNK_OWNER, lines, "team");
            teamId = ModTools.engine().replyGroup(CHUNK_OWNER, lines, "teamId");
            if (team != null && chunkButton != null && ModTools.engine().hasCommand(TEAM_INFO)) {
                chunkButton.setMessage(Component.literal("Team members"));
            }
        });
    }

    /** Sends a command whose reply is only shown, on this click only, and hands its lines back here. */
    private void ask(String commandId, Map<String, String> values, Consumer<List<String>> onReply) {
        Engine.Submission s = ModTools.engine().submit(commandId, values, response -> onReply.accept(
                response.parsed() instanceof List<?> lines && !lines.isEmpty()
                        ? lines.stream().map(String::valueOf).toList()
                        : List.of("No reply from the server. Check chat.")));
        if (!s.queued()) {
            onReply.accept(List.of("Not sent: " + String.join(", ", s.problems())));
        }
    }

    private void send() {
        Engine engine = ModTools.engine();
        Engine.Submission s = engine.submit(templateId, values(), null);
        minecraft.setScreen(parent);
        if (!s.queued()) {
            ChatPlus.INSTANCE.sendMessage(Component.literal("Not sent: " + String.join(", ", s.problems())).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Engine engine = ModTools.engine();
        TemplateRenderer.Result r = engine.render(templateId, values());
        send.active = r.ok() && ModTools.active() && System.currentTimeMillis() - openedAt >= SEND_DELAY_MS;
        super.render(g, mouseX, mouseY, partialTick);
        Component title = Component.literal(engine.label(templateId) + ": ").append(Component.literal(player).withStyle(ChatFormatting.YELLOW));
        if (offline) {
            title = title.copy().append(Component.literal(" (offline)").withStyle(ChatFormatting.GRAY));
        }
        g.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        if (recorded) {
            renderInfo(g, engine);
        }
        int y = previewY;
        if (r.ok()) {
            g.drawString(font, Component.literal("Will send:").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
            y += 11;
            for (FormattedCharSequence line : font.split(Component.literal(r.command()), columnW)) {
                g.drawString(font, line, left, y, 0xFFFF55);
                y += 10;
            }
        } else {
            g.drawString(font, Component.literal("Needs: " + String.join(", ", r.problems())).withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        }
        if (!ModTools.active()) {
            g.drawString(font, Component.literal(ModTools.status()).withStyle(ChatFormatting.RED), left, previewY + 34, 0xFFFFFF);
        }
        renderContext(g);
    }

    /** Where you are, when, and what the server's record said in this session's lookup. */
    private void renderInfo(GuiGraphics g, Engine engine) {
        int y = top + 14;
        if (!location.isEmpty()) {
            g.drawString(font, Component.literal("You: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(coords).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + place).withStyle(ChatFormatting.GRAY)), left, y, 0xFFFFFF);
        }
        y += 11;
        g.drawString(font, Component.literal("Time: ").withStyle(ChatFormatting.GRAY).append(Component.literal(time).withStyle(ChatFormatting.WHITE)), left, y, 0xFFFFFF);
        y += 11;
        LookupResult l = engine.lookup(player);
        Component record;
        if (l == null) {
            record = Component.literal("Record: no lookup this session.").withStyle(ChatFormatting.GRAY);
        } else {
            String age = PlayerListScreen.ago(System.currentTimeMillis() - engine.lookupTime(player));
            record = Component.literal("Record (" + age + "): ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(l.warnings() + " warnings, " + l.notes() + " notes, ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(l.activeBans() + " active bans").withStyle(l.activeBans() > 0 ? ChatFormatting.RED : ChatFormatting.WHITE));
        }
        g.drawString(font, record, left, y, 0xFFFFFF);
    }

    /** Below the buttons: the chunk owner reply, look-alike names, and the player's last messages. */
    private void renderContext(GuiGraphics g) {
        int y = contextY;
        if (chunkReply != null) {
            g.drawString(font, Component.literal(chunkReply.isEmpty() ? "Chunk owner: asking the server..." : "Chunk owner:").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
            y += 11;
            for (String line : chunkReply) {
                y = drawClipped(g, line, y, 0xFFDDDDDD);
            }
            y += 3;
        }
        if (teamReply != null) {
            g.drawString(font, Component.literal(teamReply.isEmpty() ? "Team " + team + ": asking the server..." : "Team " + team + ":").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
            y += 11;
            for (String line : teamReply) {
                y = drawClipped(g, line, y, 0xFFDDDDDD);
            }
            y += 3;
        }
        if (!punishing) {
            return;
        }
        for (String other : lookAlikes) {
            g.drawString(font, Component.literal("Careful: " + other + " is also online.").withStyle(ChatFormatting.GOLD), left, y, 0xFFFFFF);
            y += 11;
        }
        if (recent.isEmpty()) {
            g.drawString(font, Component.literal("No messages from them this session.").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
            return;
        }
        g.drawString(font, Component.literal("Their last messages:").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        y += 11;
        for (String message : recent) {
            y = drawClipped(g, message, y, 0xFFDDDDDD);
        }
    }

    /** One line, cut with "..." when too wide. Returns the next line's y. Lines past the bottom are skipped. */
    private int drawClipped(GuiGraphics g, String text, int y, int color) {
        if (y > height - 10) {
            return y;
        }
        String line = font.plainSubstrByWidth(text, columnW - font.width("..."));
        g.drawString(font, line.length() < text.length() ? line + "..." : line, left, y, color);
        return y + 10;
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
