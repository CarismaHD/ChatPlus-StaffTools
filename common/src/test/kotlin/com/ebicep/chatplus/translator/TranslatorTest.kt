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
    fun singleSegmentAndSeparatorSurvive() {
        val response = """[[["Hello everyone§I'm tired§Thank you very much","Bonjour tout le monde§Je suis fatigué§Merci beaucoup",null,null,3]],null,"fr",null,null,null,1,[],[["fr"],null,[1],["fr"]]]"""
        val result = GoogleRequester.parseResponse(response, english)
        assertEquals("Hello everyone§I'm tired§Thank you very much", result.message)
        assertEquals("French", result.from?.name)
    }

    @Test
    fun unknownDetectedLanguageDoesNotThrow() {
        val response = """[[["Hi","Hi",null,null,1]],null,"zz-unknown"]"""
        val result = GoogleRequester.parseResponse(response, english)
        assertEquals("Hi", result.message)
        assertNull(result.from)
    }

    @Test
    fun malformedPiecesAreSkipped() {
        val response = """[[["A ","a",null],null,[],["B","b"]],null,"es"]"""
        assertEquals("A B", GoogleRequester.parseResponse(response, english).message)
    }

    @Test
    fun selectedLinesSplitBackOnlyWhenCountsMatch() {
        assertEquals(listOf("one", "two", "three"), TranslationSplit.split("one § two§three", 3))
        assertNull(TranslationSplit.split("one two three", 3), "a lost separator must not shift lines")
        assertNull(TranslationSplit.split("a§b§c§d", 3), "an extra separator must not shift lines")
        assertEquals(listOf("only"), TranslationSplit.split("only", 1))
    }
}
