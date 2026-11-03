package com.ebicep.chatplus.translator

import com.ebicep.chatplus.ChatPlus
import com.ebicep.chatplus.hud.ChatPlusScreen
import com.ebicep.chatplus.util.ComponentUtil
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent

class SelfTranslator(val toTranslate: String, val prefix: String) : Thread() {

    override fun run() {
        val speak = LanguageManager.languageSpeak ?: return
        val translator = Translator(toTranslate, LanguageManager.languageSelf, speak)
        val translateResult = translator.translate(toTranslate)
        val reason = translator.failureReason
        if (translateResult == null && reason != null) {
            // Google could not be used. Sending the text untranslated could reach people who can't read it, so it is not
            // sent. Before, it was dropped without a word. Clicking the notice puts it back in the input box.
            val typed = sendPrefix() + toTranslate
            Minecraft.getInstance().execute {
                ChatPlus.sendMessage(
                    ComponentUtil.literal("ChatPlus: not sent, $reason. Click here to put it back.", ChatFormatting.RED)
                        .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, typed)) }
                )
            }
            return
        }
        // With nothing to translate (both languages set the same) the text is sent as typed. Before, it was dropped
        // without a word.
        val text = sendPrefix() + (translateResult?.translatedText ?: toTranslate)
        // Sending touches the connection and chat history, which belong to the client thread.
        Minecraft.getInstance().execute {
            ChatPlusScreen.sendChatMessage(message = text)
        }
    }

    /**
     * The prefix with exactly one space before the message. A command prefix used to be trimmed, so the prefix
     * "/msg Steve " and the message "hola" were sent as "/msg Stevehola" (ChatPlus fixed the same bug in 2.10.0).
     */
    private fun sendPrefix(): String = if (prefix.isBlank()) "" else prefix.trimEnd() + " "

}
