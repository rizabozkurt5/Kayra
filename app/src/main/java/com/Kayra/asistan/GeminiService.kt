package com.kayra.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiService {

    private const val SERVER_URL =
        "https://kayra-ai.bozkurtriza3842.workers.dev"

    fun ask(message: String): String {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(SERVER_URL)
                .openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            val body = JSONObject()
                .put("message", message)
                .toString()

            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream.bufferedReader().use {
                it.readText()
            }

            if (code !in 200..299) {
                return "Sunucu bağlantısında sorun oluştu."
            }

            val json = JSONObject(response)

            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } catch (e: Exception) {
            "Şu anda yapay zekâ sunucuma ulaşamıyorum."
        } finally {
            connection?.disconnect()
        }
    }
}
