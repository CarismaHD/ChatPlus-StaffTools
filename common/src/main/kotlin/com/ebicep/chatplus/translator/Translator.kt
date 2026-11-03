package com.ebicep.chatplus.translator

import com.ebicep.chatplus.ChatPlus
import com.ebicep.chatplus.config.Config
import com.ebicep.chatplus.util.ComponentUtil
import kotlinx.serialization.Serializable
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

open class Translator(val message: String, val from: Language?, val to: Language, val filtered: Boolean = true) : Thread() {

    override fun run() {
        ChatPlus.LOGGER.debug("Translating message: {} | {} -> {} | filtered: {}", message, from, to, filtered)

        val (matchedRegex, textToTranslate) = if (filtered) filterText(message) else null to message
        if (filtered && matchedRegex == null) {
            ChatPlus.LOGGER.debug("No regex match found for filtered message")
            return
        }
        val translatedMessage = translate(textToTranslate) ?: run {
            ChatPlus.LOGGER.debug("Translation failed for: $textToTranslate ($failureReason)")
            failureReason?.let { onTranslateFailed(it) }
            return
        }
        if (translatedMessage.translatedText.trim().equals(textToTranslate, ignoreCase = true)) {
            ChatPlus.LOGGER.debug("$message is the same after translation")
            onTranslateSameMessage()
            return
        }
        ChatPlus.LOGGER.debug("Translated message: ${translatedMessage.translatedText}")
        onTranslate(matchedRegex, translatedMessage, translatedMessage.from?.name)
    }

    private fun filterText(text: String): Pair<String?, String> {
        for (regexMatch in Config.values.translatorRegexes) {
            val pattern = regexMatch.pattern.takeIf { it.isNotEmpty() } ?: continue
            val matcher = Pattern.compile(pattern).matcher(text)
            if (matcher.find()) {
                val matchedRegex = matcher.group(0)
                val filteredText = text.replace(matchedRegex, "").trim()
                return matchedRegex to filteredText
            }
        }
        return null to text
    }

    open fun onTranslateSameMessage() {

    }

    /**
     * Why the last [translate] call failed, in words a player can act on. Null when it succeeded, or when there was
     * nothing to translate (blank text, or the same language on both sides).
     */
    var failureReason: String? = null
        private set

    /** Called when Google could not be used. Automatic translation stays quiet; a Ctrl+click reports it. */
    open fun onTranslateFailed(reason: String) {

    }

    open fun onTranslate(matchedRegex: String?, translatedMessage: TranslateResult, fromLanguage: String?) {
        // This runs on the translation thread. Chat may only be changed from the client thread.
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.sendSystemMessage(
                ComponentUtil.literalIgnored(
                    (matchedRegex ?: "") + translatedMessage.translatedText + " (" + (fromLanguage ?: "Unknown") + ")",
                    ComponentUtil.LiteralIgnoredType.TRANSLATE
                ).withStyle(ChatFormatting.GREEN)
            )
        }
    }

    fun translate(text: String): TranslateResult? {
        failureReason = null
        if (from == to) {
            return null
        }
        if (text.trim().isEmpty()) {
            return null
        }
        if (GoogleRequester.accessDenied) {
            failureReason = "Translation is paused after a Google error, try again in ${GoogleRequester.pauseSecondsLeft}s"
            return null
        }
        //Use free ones later
        val google = GoogleRequester()
        val transRequest: RequestResult = if (from == null) google.translateAuto(text, to) else google.performTranslationRequest(text, from, to)
        if (transRequest.code != 200) {
            logException(transRequest)
            failureReason = when (transRequest.code) {
                1 -> "Could not reach Google Translate"
                429 -> "Google is rate limiting translations, paused for 5 minutes"
                411 -> "Google Translate returned an error, paused for 30 seconds"
                else -> "Translation failed: ${transRequest.message}"
            }
            return null
        }
        // The detected language is only used for the "(Language)" suffix, so an unknown one is no reason to drop
        // the translation.
        return TranslateResult(transRequest.message.trim(), transRequest.from)
    }

    private fun logException(transRequest: RequestResult) {
        when (transRequest.code) {
            1 -> ChatPlus.LOGGER.error("Cannot connect to translation server. Is player offline?")
            2 -> ChatPlus.LOGGER.error(transRequest.message)
            411 -> ChatPlus.LOGGER.error("Google API >> API call error")
            429 -> ChatPlus.LOGGER.warn("Google denied access to translation API. Pausing translation for 5 minutes")
            403 -> ChatPlus.LOGGER.error("Google API >> Exceeded API quota / User rate limit reached")
            400 -> ChatPlus.LOGGER.error("Google API >> API key invalid")
            500 -> ChatPlus.LOGGER.error("Google API >> Failed to determine source language: " + transRequest.message)
            else -> ChatPlus.LOGGER.error("Unknown error/Server side failure: " + transRequest.message)
        }
    }

}


data class TranslateResult(val translatedText: String, val from: Language?)

object TranslationSplit {
    /**
     * Several selected messages are sent as one request, one per line. A line break is plain text that Google keeps in
     * its output, and each message stays a sentence of its own. The old "§" separator is Minecraft's colour-code
     * character, which Google sometimes dropped or moved. If the lines still come back merged, [split] notices.
     */
    const val SEPARATOR = "\n"

    /**
     * Splits a translation back into one piece per message. Returns null when the piece count no longer matches,
     * rather than pairing a translation with the wrong message.
     */
    fun split(translated: String, lineCount: Int): List<String>? {
        val pieces = translated.split(SEPARATOR).map { it.trim() }.let { all ->
            // A trailing newline from Google adds an empty last piece.
            if (all.size == lineCount + 1 && all.last().isEmpty()) all.dropLast(1) else all
        }
        return if (pieces.size == lineCount) pieces else null
    }
}

/**
 * Splits a chat line into the sender part ("[Admin] Steve: ", "<Steve> ", "Steve » ") and the message. Only the
 * message is sent to Google: rank tags and player names are not words in any language, so translating them mangled
 * names and pushed language detection the wrong way, which made short messages come back wrong.
 */
object SenderPrefix {
    /**
     * Bracketed tags, then "<name>" or one word followed by ":", "»", ">", "|" or "->". Only one word before the
     * separator: allowing two also caught ordinary sentences such as "Look here: ...", which then stayed untranslated.
     * A server format this misses can be covered with a translator regex, which is tried first.
     */
    private val PREFIX = Regex(
        """^(?:\[[^\]]{1,32}]\s*)*(?:<[^>]{1,40}>|[^\s:»>|]{1,32}\s*(?:[:»>|]|->))\s+"""
    )

    /** Returns the prefix (kept as it was) and the text to translate. The prefix is empty when none is found. */
    fun split(line: String, userPatterns: List<String> = emptyList()): Pair<String, String> {
        for (pattern in userPatterns) {
            if (pattern.isEmpty()) continue
            val match = runCatching { Regex(pattern).find(line) }.getOrNull() ?: continue
            if (match.range.first == 0 && match.value.isNotEmpty() && match.range.last < line.length - 1) {
                return match.value to line.substring(match.range.last + 1).trim()
            }
        }
        val first = PREFIX.find(line) ?: return "" to line
        var prefixEnd = first.range.last + 1
        // "Steve -> me: hi" and "Global | Bob: hi" have two parts before the message. Only those separators get a
        // second round, so a colon inside the message itself is left alone.
        val separator = first.value.trimEnd()
        if (separator.endsWith("->") || separator.endsWith("|")) {
            PREFIX.find(line.substring(prefixEnd))?.let { prefixEnd += it.range.last + 1 }
        }
        // The message can be left with nothing to translate ("Steve: :)"). The caller checks that, rather than this
        // sending the name to Google.
        return line.substring(0, prefixEnd) to line.substring(prefixEnd).trim()
    }
}

@Serializable
data class RegexMatch(var match: String, var senderNameGroupIndex: Int)