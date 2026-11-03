package com.ebicep.chatplus.features

import com.ebicep.chatplus.ChatPlus
import com.ebicep.chatplus.config.Config.values
import com.ebicep.chatplus.events.EventBus
import com.ebicep.chatplus.features.chattabs.AddNewMessageEvent
import com.ebicep.chatplus.features.chattabs.ChatTab
import com.ebicep.chatplus.features.textbarelements.AddTextBarElementEvent
import com.ebicep.chatplus.features.textbarelements.FindToggleEvent
import com.ebicep.chatplus.features.textbarelements.TranslateSpeakTextBarElement
import com.ebicep.chatplus.hud.*
import com.ebicep.chatplus.hud.ChatPlusScreen.EDIT_BOX_DISPLAY_HEIGHT
import com.ebicep.chatplus.hud.ChatPlusScreen.EDIT_BOX_HEIGHT
import com.ebicep.chatplus.mixin.IMixinChatScreen
import com.ebicep.chatplus.mixin.IMixinScreen
import com.ebicep.chatplus.translator.*
import com.ebicep.chatplus.util.ComponentUtil
import dev.architectury.event.EventResult
import dev.architectury.event.events.client.ClientRawInputEvent
import net.minecraft.ChatFormatting
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.util.Optional


object TranslateMessage {

    val TRANSLATE_PREFIX_INPUT_WIDTH: Int
        get() = if (values.vanillaInputBox) 63 else 65
    var languageSpeakEnabled = false
    var inputTranslatePrefix: EditBox? = null

    init {
        EventBus.register<AddTextBarElementEvent>({ 0 }) {
            if (!values.translatorEnabled) {
                return@register
            }
            if (!values.translatorTextBarElementEnabled) {
                return@register
            }
            val textBarElement = TranslateSpeakTextBarElement(it.screen)
            textBarElement.init()
            it.elements.add(textBarElement)
        }
        EventBus.register<ChatScreenInitPostEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            val screen = it.screen

            inputTranslatePrefix = null
            if (languageSpeakEnabled) {
                val inputBoxSettings = values.inputBoxSettings
                screen as IMixinChatScreen
                screen.input?.x = TRANSLATE_PREFIX_INPUT_WIDTH + (if (values.vanillaInputBox) 5 else inputBoxSettings.startX)
                screen.input.width -= TRANSLATE_PREFIX_INPUT_WIDTH
                inputTranslatePrefix = EditBox(
                    screen.minecraft!!.fontFilterFishy,
                    if (values.vanillaInputBox) 4 else inputBoxSettings.startX,
                    if (values.vanillaInputBox) screen.height - EDIT_BOX_HEIGHT + 2 else inputBoxSettings.getCalculatedStartY(),
                    TRANSLATE_PREFIX_INPUT_WIDTH,
                    EDIT_BOX_DISPLAY_HEIGHT,
                    Component.translatable("chatPlus.editBox")
                )
                val editBox = inputTranslatePrefix as EditBox
                editBox.setMaxLength(inputBoxSettings.maxInputBoxInputLength) // default 256
                editBox.isBordered = false
                editBox.setCanLoseFocus(true)
                screen as IMixinScreen
                screen.callAddWidget(editBox)
            }
        }
        EventBus.register<ChatScreenCloseEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            if (languageSpeakEnabled && !values.translateKeepOnAfterChatClose) {
                languageSpeakEnabled = false
            }
        }
        EventBus.register<FindToggleEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            if (languageSpeakEnabled) {
                languageSpeakEnabled = false
            }
        }
        EventBus.register<ChatScreenMouseClickedEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            if (languageSpeakEnabled) {
                // Keeps a click another handler already used, such as a Ctrl+click translation. Overwriting it let the
                // clicked text's own action (a link, a command) run as well.
                it.returnFunction = it.returnFunction || inputTranslatePrefix != null &&
                        inputTranslatePrefix!!.isFocused &&
                        inputTranslatePrefix!!.mouseClicked(it.mouseX, it.mouseY, it.button)
            }
        }
        EventBus.register<ChatScreenRenderEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            if (inputTranslatePrefix == null) {
                return@register
            }
            val screen = it.screen
            val guiGraphics = it.guiGraphics
            val height = screen.height
            val minecraft = screen.minecraft!!
            val inputBoxSettings = values.inputBoxSettings
            val startX = if (values.vanillaInputBox) 2 else inputBoxSettings.startX - 2
            val startY = if (values.vanillaInputBox) height - EDIT_BOX_HEIGHT else inputBoxSettings.getCalculatedStartY() - MovableChat.InputBoxSettings.INPUT_BOX_PADDING
            guiGraphics.fill(
                startX,
                startY,
                startX + TRANSLATE_PREFIX_INPUT_WIDTH,
                if (values.vanillaInputBox) height - 2 else inputBoxSettings.getCalculatedStartY() + MovableChat.InputBoxSettings.PADDED_INPUT_BOX_HEIGHT,
                minecraft.options.getBackgroundColor(Int.MIN_VALUE)
            )
            guiGraphics.renderOutline(
                startX,
                startY,
                TRANSLATE_PREFIX_INPUT_WIDTH,
                EDIT_BOX_DISPLAY_HEIGHT,
                0xFF55FF55.toInt()
            )
            val mouseX = it.mouseX
            val mouseY = it.mouseY
            inputTranslatePrefix!!.render(guiGraphics, mouseX, mouseY, it.partialTick)
            if (
                mouseX in startX until startX + TRANSLATE_PREFIX_INPUT_WIDTH &&
                mouseY in startY until startY + EDIT_BOX_HEIGHT
            ) {
                guiGraphics.renderTooltip(
                    minecraft.font,
                    Component.translatable("chatPlus.translator.translateSpeakPrefix.tooltip"),
                    mouseX,
                    mouseY
                )
            }
        }
        EventBus.register<ChatScreenSendMessagePostEvent> {
            if (!values.translatorEnabled) {
                return@register
            }
            if (!languageSpeakEnabled) {
                return@register
            }
            // Commands are sent as typed. Translating them turned "/home" into Google's translation of it, and since
            // speak mode stays on after chat closes, that easily happened by accident.
            if (it.normalizeChatMessage.startsWith("/")) {
                return@register
            }
            it.dontSendMessage = true
            SelfTranslator(it.normalizeChatMessage, if (inputTranslatePrefix == null) "" else inputTranslatePrefix!!.value).start()
        }
        EventBus.register<AddNewMessageEvent>({ -10 }) {
            if (!isTranslateMessage(it.rawComponent)) {
                handleTranslate(it.rawComponent)
            }
        }
        ClientRawInputEvent.KEY_PRESSED.register { _, keyCode, _, _, modifiers ->
            if (ChatManager.isChatFocused()) {
                return@register EventResult.pass()
            }
            if (keyCode != values.translateKey.key.value || modifiers != values.translateKey.modifier.toInt()) {
                return@register EventResult.pass()
            }
            languageSpeakEnabled = true
            EventResult.interruptTrue()
        }
        EventBus.register<ChatScreenInputEvent> {
            if (it.checkRelease(values.translateToggleKey)) {
                return@register
            }
            TranslateSpeakTextBarElement.toggleTranslateSpeak(it.screen)
        }
        // Guards against a double click sending the same request twice. It used to be a flat 2 seconds for any message,
        // so a second Ctrl+click on another line soon after did nothing at all.
        var lastClickTime = 0L
        var lastClickMessage: Any? = null
        EventBus.register<ChatScreenMouseClickedEvent>({ 100 }) {
            if (!values.translatorEnabled || !values.translateClickEnabled) {
                return@register
            }
            if (it.button != 0 || !Screen.hasControlDown()) {
                return@register
            }
            val message = ChatManager.globalSelectedTab.getHoveredOverMessageLine(it.mouseX, it.mouseY) ?: return@register
            // From here the click is a translate click, so it is used even when it cannot translate. Otherwise the
            // clicked text's own action, such as a link or a /msg suggestion on a name, ran instead.
            it.returnFunction = true
            val now = System.currentTimeMillis()
            if (message.linkedMessage == lastClickMessage && now - lastClickTime < 1_500) {
                return@register
            }
            if (now - lastClickTime < 300) {
                return@register
            }
            val to = LanguageManager.targetLanguage() ?: run {
                ChatPlus.sendMessage(
                    Component.literal("ChatPlus: choose a \"Translate To\" language in the translator settings first")
                        .withStyle(ChatFormatting.RED)
                )
                return@register
            }
            lastClickTime = now
            lastClickMessage = message.linkedMessage
            // Selected messages are translated in one request, one per line, then split back per message.
            val selectedMessages = SelectChat.getAllSelectedMessages()
            val messages: List<ChatTab.ChatPlusGuiMessage> = if (selectedMessages.contains(message)) {
                SelectChat.getSelectedMessagesOrdered().map { it.linkedMessage }.distinct()
            } else {
                listOf(message.linkedMessage)
            }
            val userPatterns = values.translatorRegexes.map { it.pattern }
            // Messages with nothing to translate (only a name, numbers or emoticons) are left out, so the rest still
            // line up with their translations.
            val parts = messages
                .map { msg -> msg to SenderPrefix.split(messageText(msg.guiMessage.content), userPatterns) }
                .filter { (_, split) -> split.second.any { c -> c.isLetter() } }
            if (parts.isEmpty()) {
                ChatPlus.sendMessage(ComponentUtil.literal("ChatPlus: nothing to translate in that message", ChatFormatting.RED))
                return@register
            }
            ClickTranslator(parts.map { it.first }, parts.map { it.second.first }, parts.map { it.second.second }, to).start()
        }
    }

    /**
     * A chat message's text without the timestamp and repeat counter ChatPlus may add to it, so neither is sent to Google
     * or mistaken for the sender's name.
     */
    private fun messageText(component: Component): String {
        val text = StringBuilder()
        fun collect(part: Component) {
            val contents = part.contents
            val addedByChatPlus = contents is ComponentUtil.LiteralContentsIgnored &&
                    (contents.isType(ComponentUtil.LiteralIgnoredType.TIMESTAMP) ||
                            contents.isType(ComponentUtil.LiteralIgnoredType.COMPACT))
            if (!addedByChatPlus) {
                contents.visit<Unit> { piece ->
                    text.append(piece)
                    Optional.empty()
                }
            }
            part.siblings.forEach { collect(it) }
        }
        collect(component)
        return ChatFormatting.stripFormatting(text.toString())!!.replace("\n", " ")
    }

    private fun isTranslateMessage(component: Component): Boolean {
        return component.contents is ComponentUtil.LiteralContentsIgnored &&
                (component.contents as ComponentUtil.LiteralContentsIgnored).isType(ComponentUtil.LiteralIgnoredType.TRANSLATE)
    }

    private fun handleTranslate(component: Component) {
        if (!values.translatorEnabled) {
            return
        }
        // Automatic translation only applies to lines matching a translator regex. With none set, skip the thread
        // that every incoming message used to start just to find that out.
        if (values.translatorRegexes.none { it.pattern.isNotEmpty() }) {
            return
        }
        LanguageManager.targetLanguage()?.let {
            Translator(ChatFormatting.stripFormatting(component.string)!!, null, it).start()
        }
    }

    class ClickTranslator(
        val line: List<ChatTab.ChatPlusGuiMessage>,
        private val prefixes: List<String>,
        bodies: List<String>,
        to: Language
    ) : Translator(bodies.joinToString(TranslationSplit.SEPARATOR), null, to, false) {

        private fun originals(): Component {
            val component = Component.literal("")
            line.forEachIndexed { index, it ->
                component.append(it.guiMessage.content)
                if (index != line.size - 1) {
                    component.append(Component.literal("\n"))
                }
            }
            return component
        }

        override fun onTranslateSameMessage() {
            // Runs on the translation thread. Chat may only be changed from the client thread.
            Minecraft.getInstance().execute {
                ChatPlus.sendMessage(
                    ComponentUtil.translatable(
                        "chatPlus.translator.sameMessage",
                        ChatFormatting.RED,
                        HoverEvent(HoverEvent.Action.SHOW_TEXT, originals())
                    )
                )
            }
        }

        override fun onTranslateFailed(reason: String) {
            Minecraft.getInstance().execute {
                ChatPlus.sendMessage(
                    ComponentUtil.literal("ChatPlus: $reason", ChatFormatting.RED, HoverEvent(HoverEvent.Action.SHOW_TEXT, originals()))
                )
            }
        }

        override fun onTranslate(matchedRegex: String?, translatedMessage: TranslateResult, fromLanguage: String?) {
            val suffix = " (" + (fromLanguage ?: "Unknown") + ")"
            val pieces = TranslationSplit.split(translatedMessage.translatedText, line.size)
            // Everything that reads the chat messages runs on the client thread, like the change to chat itself.
            Minecraft.getInstance().execute {
                // When the pieces no longer line up with the selected lines, show the whole translation as one message,
                // with every original line in the hover, rather than pairing text with the wrong line.
                val messages: List<Pair<String, Component>> = if (pieces != null) {
                    pieces.mapIndexed { index, piece -> prefixes[index] + piece to line[index].guiMessage.content.copy() }
                } else {
                    listOf(translatedMessage.translatedText.replace(TranslationSplit.SEPARATOR, " / ").trim() to originals())
                }
                messages.forEach { (text, original) ->
                    val component = ComponentUtil.literal(
                        text + suffix,
                        ChatFormatting.GREEN,
                        HoverEvent(HoverEvent.Action.SHOW_TEXT, original)
                    )
                    ChatManager.globalSelectedTab.addNewMessage(
                        AddNewMessageEvent(
                            component.copy(),
                            component,
                            null,
                            null,
                            Minecraft.getInstance().gui.guiTicks,
                            GuiMessageTag.system(),
                            false
                        )
                    )
                }
            }
        }

    }

}
