package com.ebicep.chatplus.translator

import com.ebicep.chatplus.ChatPlus
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class GoogleRequester {

    companion object {
        const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
        private const val READ_TIMEOUT_MS = 10_000
        private const val USER_AGENT = "Mozilla/5.0 (ChatPlus)"

        @Volatile
        var accessDenied = false

        /**
         * Reads Google's response. Each sentence piece already carries its own trailing space, so pieces are joined
         * as they are. Joining them with a space doubled the gap between sentences.
         */
        fun parseResponse(entity: String, targetLanguage: Language): RequestResult {
            val gson: Gson = GsonBuilder().setLenient().create()
            val json: JsonArray = gson.fromJson(entity, JsonArray::class.java)
            val detectedCode = json.get(2).takeIf { it.isJsonPrimitive }?.asString
            val detectedSource = detectedCode?.let { LanguageManager.findLanguageFromGoogle(it) }
            val translatedText = json[0].asJsonArray
                .filter { it.isJsonArray && it.asJsonArray.size() > 0 && it.asJsonArray[0].isJsonPrimitive }
                .joinToString("") { it.asJsonArray[0].asString }
            return RequestResult(200, translatedText, detectedSource, targetLanguage)
        }
    }

    fun translateAuto(message: String, to: Language?): RequestResult {
        return performTranslationRequest(message, LanguageManager.autoLang, to!!)
    }

    fun performTranslationRequest(message: String, from: Language, to: Language): RequestResult {
        val encodedMessage = encodeMessage(message) ?: return RequestResult(2, "Failed to encode message", null, null)
        val queryParams = mutableMapOf(
            "client" to "gtx",
            "sl" to from.googleCode,
            "tl" to to.googleCode,
            "dt" to "t",
            "q" to encodedMessage
        )

        return try {
            val response = sendRequest("GET", queryParams, "application/json")
                ?: return RequestResult(1, "Connection error", null, null)

            when (response.responseCode) {
                200 -> processSuccessfulResponse(response, to)
                429 -> handleAccessDenied()
                else -> handleApiError(response)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RequestResult(1, "Connection error", null, null)
        }
    }

    private fun encodeMessage(message: String): String? {
        return try {
            URLEncoder.encode(message, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            null
        }
    }

    private fun processSuccessfulResponse(response: Response, targetLanguage: Language): RequestResult {
        return try {
            parseResponse(response.entity, targetLanguage)
        } catch (e: Exception) {
            ChatPlus.LOGGER.error("Unexpected translation response", e)
            RequestResult(500, "Unexpected response from Google", null, null)
        }
    }

    private fun handleAccessDenied(): RequestResult {
        pause()
        return RequestResult(429, "Access to Google Translate denied", null, null)
    }

    /** Any other error pauses translation too, but only for a while. Before, it stopped translation until a restart. */
    private fun handleApiError(response: Response): RequestResult {
        pause()
        ChatPlus.LOGGER.error(response.entity)
        return RequestResult(411, "API call error", null, null)
    }

    private fun pause() {
        synchronized(GoogleRequester::class.java) {
            if (!accessDenied) {
                accessDenied = true
                Timeout().start()
            }
        }
    }

    private fun sendRequest(method: String, queryParams: Map<String, String>, contentType: String): Response? {
        val requestUrl = buildRequestUrl(queryParams)

        return try {
            val connection = createConnection(requestUrl, method, contentType)
            val responseBody = readResponseBody(connection)
            Response(connection.responseCode, responseBody)
        } catch (e: Exception) {
            when (e) {
                is MalformedURLException -> null
                is IOException -> Response(1, "Failed to connect to server")
                else -> {
                    ChatPlus.LOGGER.error(e)
                    Response(1, "Unexpected error: ${e.message}")
                }
            }
        }
    }

    private fun buildRequestUrl(queryParams: Map<String, String>): String {
        return queryParams.entries.joinToString("&", "$BASE_URL?") { (key, value) ->
            "$key=$value"
        }
    }

    private fun createConnection(requestUrl: String, method: String, contentType: String): HttpsURLConnection {
        return (URL(requestUrl).openConnection() as HttpsURLConnection).apply {
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("User-Agent", USER_AGENT)
            requestMethod = method
            connectTimeout = 5000
            // Without this a stalled request held its thread forever.
            readTimeout = READ_TIMEOUT_MS
            connect()
        }
    }

    private fun readResponseBody(connection: HttpsURLConnection): String {
        val inputStream = if (connection.responseCode == HttpsURLConnection.HTTP_OK)
            connection.inputStream
        else
            connection.errorStream

        return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
}

data class RequestResult(val code: Int, val message: String, val from: Language?, val to: Language?)

data class Response(val responseCode: Int, val entity: String)

class Timeout : Thread("ChatPlus-TranslatePause") {
    init {
        isDaemon = true
    }

    override fun run() {
        try {
            sleep(300000)
            GoogleRequester.accessDenied = false
        } catch (ignored: InterruptedException) {
        }
    }
}