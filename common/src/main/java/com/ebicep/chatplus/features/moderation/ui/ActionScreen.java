package com.ebicep.chatplus.features.moderation.ui;

import com.ebicep.chatplus.ChatPlus;
import com.ebicep.chatplus.features.moderation.ModTools;
import com.ebicep.chatplus.features.moderation.command.CommandTemplate;
import com.ebicep.chatplus.features.moderation.command.TemplateRenderer;
import com.ebicep.chatplus.features.moderation.core.Engine;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Asks for what a command needs, shows the exact command, and sends only on Send. It is the confirmation for every
 * command that changes something. Uses only vanilla widgets.
 */
final class ActionScreen extends Screen {
    /** Send ignores clicks at first, so a double-click on the menu behind cannot confirm by accident. */
    private static final long SEND_DELAY_MS = 500;

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
    private String text = "";
    private String duration = "";
    private boolean silent;
    private Button send;
    private int left;
    private int columnW;
    private int previewY;

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
        int y = height / 2 - 50;
        if (needsText) {
            EditBox box = new EditBox(font, left, y, columnW, 20, Component.literal(textLabel));
            box.setMaxLength(256);
            box.setHint(Component.literal(textLabel).withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(text);
            box.setResponder(s -> text = s);
            addRenderableWidget(box);
            setInitialFocus(box);
            y += 24;
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
        g.drawCenteredString(font, Component.literal(engine.label(templateId) + ": ").append(Component.literal(player).withStyle(ChatFormatting.YELLOW)),
                width / 2, height / 2 - 70, 0xFFFFFF);
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
