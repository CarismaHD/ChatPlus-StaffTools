package com.ebicep.chatplus.translator

import com.ebicep.chatplus.ChatPlus
import com.ebicep.chatplus.config.Config
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*


object LanguageManager {

    var languages: List<Language> = ArrayList()
    var languageTo: Language? = null
    var languageSelf: Language? = null
    var languageSpeak: Language? = null
    var languageSpeakEnabled = false

    val autoLang: Language

    init {
        ChatPlus.LOGGER.info("Loading languages...")
        val inputStream = LanguageManager::class.java.getResourceAsStream("/assets/chatplus/lang.json")
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val gson = Gson()
        val langArray: Array<Language> = gson.fromJson(reader, Array<Language>::class.java)
        languages = listOf(*langArray)
        autoLang = findLanguageFromGoogle("auto")!! //TODO
        ChatPlus.LOGGER.info("Loaded ${languages.size} languages")
    }

    fun updateTranslateLanguages() {
        languageTo = findLanguageFromName(Config.values.translateTo)
//        ChatPlus.LOGGER.info("LanguageTo: $languageTo")
        languageSelf = findLanguageFromName(Config.values.translateSelf)
//        ChatPlus.LOGGER.info("LanguageSelf: $languageSelf")
        languageSpeak = findLanguageFromName(Config.values.translateSpeak)
//        ChatPlus.LOGGER.info("LanguageSpeak: $languageSpeak")
    }

    fun findLanguageFromName(name: String): Language? {
        for (lang in languages) {
            if (lang.name == name) {
                return lang
            }
        }
        return null
    }

    /**
     * Google's own codes for some languages differ from lang.json (Hebrew is "iw" and Javanese "jw" on Google
     * Translate), and it can detect languages the list does not have at all. Before, any of those left the source
     * language null and the translation was dropped without a word, which is one reason Ctrl+click sometimes did
     * nothing. The Chinese and Filipino entries are there in case Google reports those the same way.
     */
    private val googleAliases = mapOf(
        "iw" to "he",
        "jw" to "jv",
        "zh" to "zh-CN",
        "zh-Hans" to "zh-CN",
        "zh-Hant" to "zh-TW",
        "fil" to "tl",
    )

    fun findDetectedLanguage(googleCode: String): Language =
        findLanguageFromGoogle(googleAliases[googleCode] ?: googleCode) ?: Language(googleCode, googleCode, googleCode)

    fun findLanguageFromGoogle(googleCode: String): Language? {
        for (lang in languages) {
            if (lang.googleCode == googleCode) {
                return lang
            }
        }
        return null
    }

}