package com.kayra.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls the Cloudflare Worker. The Gemini API key stays on the server, never in the APK. */
object GeminiService {
    private const val SERVER_URL = "https://kayra-ai.bozkurtriza3842.workers.dev"

    fun ask(message: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(SERVER_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().put("message", message).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val details = runCatching { JSONObject(body).optString("details") }
                    .getOrDefault(body)
                    .ifBlank { "Sunucu isteği kabul etmedi." }
                return "Sunucu hatası (HTTP $status): $details"
            }

            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            val answer = parts?.optJSONObject(0)?.optString("text").orEmpty()
            answer.ifBlank {
                val messageText = json.optString("error").ifBlank { "Yapay zekâ boş yanıt verdi." }
                "KAYRA: $messageText"
            }
        } catch (e: Exception) {
            "Bağlantı kurulamadı. İnternetini ve KAYRA sunucusunu kontrol et. (${e.localizedMessage ?: "bilinmeyen hata"})"
        } finally {
            connection?.disconnect()
        }
    }
}
