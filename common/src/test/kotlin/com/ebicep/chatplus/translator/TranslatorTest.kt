package com.ebicep.chatplus.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Recorded Google responses, so the parsing is checked without a network call. */
class TranslatorTest {
    private val english = LanguageManager.findLanguageFromName("English")!!

    @Test
    fun sentencesAreJoinedWithoutDoubleSpaces() {
        val response = """[[["Hi, friend. ","Hola amigo. ",null,null,10],["How are you? ","¿Cómo estás? ",null,null,10],["I like this server.","Me gusta este servidor.",null,null,3,null,null,[[]],[[["d0","es_en_2023q1.md"]]]]],null,"es",null,null,null,1,[],[["es"],null,[1],["es"]]]"""
        val result = GoogleRequester.parseResponse(response, english)
        assertEquals(200, result.code)
        assertEquals("Hi, friend. How are you? I like this server.", result.message)
        assertEquals("Spanish", result.from?.name)
    }

    @Test
    fun newlineSeparatedLinesSurvive() {
        val response = """[[["Hello everyone\n","Bonjour tout le monde\n",null,null,3],["I'm tired\n","Je suis fatigué\n",null,null,3],["Thank you very much","Merci beaucoup",null,null,3]],null,"fr",null,null,null,1,[],[["fr"],null,[1],["fr"]]]"""
        val result = GoogleRequester.parseResponse(response, english)
        assertEquals("Hello everyone\nI'm tired\nThank you very much", result.message)
        assertEquals("French", result.from?.name)
    }

    @Test
    fun unknownDetectedLanguageStillTranslates() {
        val response = """[[["Hi","Hi",null,null,1]],null,"zz-unknown"]"""
        val result = GoogleRequester.parseResponse(response, english)
        assertEquals("Hi", result.message)
        assertEquals("zz-unknown", result.from?.name)
    }

    @Test
    fun oldGoogleCodesMapToListedLanguages() {
        assertEquals("he", LanguageManager.findDetectedLanguage("iw").googleCode)
        assertEquals("jv", LanguageManager.findDetectedLanguage("jw").googleCode)
        assertEquals("zh-CN", LanguageManager.findDetectedLanguage("zh").googleCode)
    }

    @Test
    fun senderPrefixIsKeptOutOfTheTranslation() {
        assertEquals("[Admin] Steve: " to "hola a todos", SenderPrefix.split("[Admin] Steve: hola a todos"))
        assertEquals("<Steve> " to "bonjour", SenderPrefix.split("<Steve> bonjour"))
        assertEquals("[VIP] [Lv 5] Alex » " to "wie geht's", SenderPrefix.split("[VIP] [Lv 5] Alex » wie geht's"))
        assertEquals("Steve -> me: " to "salut", SenderPrefix.split("Steve -> me: salut"))
        assertEquals("Global | Bob: " to "ciao", SenderPrefix.split("Global | Bob: ciao"))
        assertEquals("" to "hola amigos como estan", SenderPrefix.split("hola amigos como estan"))
    }

    @Test
    fun ordinarySentencesKeepTheirFirstWords() {
        assertEquals("" to "Mira esto: un creeper", SenderPrefix.split("Mira esto: un creeper"))
        assertEquals("Steve: " to "mira: un creeper", SenderPrefix.split("Steve: mira: un creeper"))
        assertEquals("Steve: " to ":)", SenderPrefix.split("Steve: :)"), "the name is never sent, even with nothing after it")
    }

    @Test
    fun translatorRegexesAreTriedFirst() {
        assertEquals("Global Bob ~ " to "ciao", SenderPrefix.split("Global Bob ~ ciao", listOf("^Global \\w+ ~ ")))
        assertEquals("" to "ciao", SenderPrefix.split("ciao", listOf("(?s).*")), "a regex matching the whole line is not a prefix")
        assertEquals("Bob: " to "ciao", SenderPrefix.split("Bob: ciao", listOf("[")), "a broken regex is skipped")
    }

    @Test
    fun malformedPiecesAreSkipped() {
        val response = """[[["A ","a",null],null,[],["B","b"]],null,"es"]"""
        assertEquals("A B", GoogleRequester.parseResponse(response, english).message)
    }

    @Test
    fun selectedLinesSplitBackOnlyWhenCountsMatch() {
        assertEquals(listOf("one", "two", "three"), TranslationSplit.split("one \n two\nthree", 3))
        assertEquals(listOf("one", "two"), TranslationSplit.split("one\ntwo\n", 2))
        assertNull(TranslationSplit.split("one two three", 3), "a lost separator must not shift lines")
        assertNull(TranslationSplit.split("a\nb\nc\nd", 3), "an extra separator must not shift lines")
        assertEquals(listOf("only"), TranslationSplit.split("only", 1))
    }
}
