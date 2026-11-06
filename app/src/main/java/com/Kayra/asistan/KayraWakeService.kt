package com.kayra.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Locale

/**
 * Visible foreground microphone service for the "Hey Kayra" wake phrase.
 * Android's SpeechRecognizer is not a dedicated always-on hotword engine:
 * availability and screen-off behavior depend on the device's recognition provider.
 */
class KayraWakeService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_STOP = "com.kayra.assistant.action.STOP_WAKE"
        private const val CHANNEL_ID = "kayra_wake_channel"
        private const val NOTIFICATION_ID = 731
    }

    private enum class Mode { WAITING_FOR_WAKE, WAITING_FOR_COMMAND, THINKING }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mode = Mode.WAITING_FOR_WAKE
    private var restarting = false
    private var serviceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        createRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        serviceRunning = true
        startRecognition()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("tr", "TR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.92f)
        }
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { restarting = false }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 700L)
            }
            override fun onResults(results: android.os.Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                handleRecognizedSpeech(spoken)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }

    private fun startRecognition() {
        if (restarting || !isForegroundServiceActive()) return
        if (recognizer == null) createRecognizer()
        val current = recognizer ?: run {
            updateNotification("Konuşma tanıma bu telefonda kullanılamıyor")
            scheduleRestart(5000L)
            return
        }

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            current.startListening(speechIntent)
            updateNotification(
                if (mode == Mode.WAITING_FOR_WAKE) "“Hey Kayra” komutunu bekliyor"
                else "Komutunu dinliyor"
            )
        } catch (_: Exception) {
            resetRecognizer()
            scheduleRestart(1200L)
        }
    }

    private fun handleRecognizedSpeech(raw: String) {
        val spoken = raw.trim()
        val normalized = spoken.lowercase(Locale("tr", "TR"))
            .replace(Regex("[^a-z0-9çğıöşü ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        when (mode) {
            Mode.WAITING_FOR_WAKE -> {
                val wakeIndex = normalized.indexOf("hey kayra")
                if (wakeIndex >= 0) {
                    val remainder = normalized.substring(wakeIndex + "hey kayra".length).trim()
                    if (remainder.isNotBlank()) {
                        processCommand(remainder)
                    } else {
                        mode = Mode.WAITING_FOR_COMMAND
                        updateNotification("Uyandı • Komutunu bekliyor")
                        speakAndThenListen("Buradayım. Seni dinliyorum.")
                    }
                } else {
                    scheduleRestart(450L)
                }
            }
            Mode.WAITING_FOR_COMMAND -> {
                if (normalized.isBlank()) {
                    speakAndThenListen("Seni duyamadım. Tekrar söyleyebilirsin.")
                } else {
                    processCommand(spoken)
                }
            }
            Mode.THINKING -> scheduleRestart(800L)
        }
    }

    private fun processCommand(command: String) {
        mode = Mode.THINKING
        updateNotification("Komutun işleniyor")
        if (command.equals("iptal", ignoreCase = true) ||
            command.equals("bekleme modu", ignoreCase = true)
        ) {
            mode = Mode.WAITING_FOR_WAKE
            speakAndThenListen("Tamam. Yeniden Hey Kayra demeni bekliyorum.")
            return
        }

        Thread {
            val prompt = "Sen KAYRA adında Türkçe konuşan, doğal, yardımsever bir kişisel asistansın. " +
                "Kullanıcı sana sesli komut verdi. Kısa ve anlaşılır Türkçe cevap ver. " +
                "Telefonda doğrudan yapamadığın işlemleri yaptığını iddia etme.\nKullanıcı: $command\nKAYRA:"
            val answer = GeminiService.ask(prompt)
            handler.post {
                if (mode == Mode.THINKING) {
                    mode = Mode.WAITING_FOR_WAKE
                    speakAndThenListen(answer)
                }
            }
        }.start()
    }

    private fun speakAndThenListen(message: String) {
        updateNotification(message.take(70))
        if (!ttsReady || tts == null) {
            scheduleRestart(500L)
            return
        }
        val utteranceId = "KAYRA_WAKE_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { scheduleRestart(500L) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { scheduleRestart(700L) }
            }
        })
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun scheduleRestart(delay: Long) {
        if (restarting) return
        restarting = true
        handler.postDelayed({
            restarting = false
            if (recognizer == null) createRecognizer()
            startRecognition()
        }, delay)
    }

    private fun resetRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun isForegroundServiceActive(): Boolean = serviceRunning

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KAYRA uyandırma servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hey Kayra uyandırma modu açıkken görünür"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = openApp?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KAYRA • Hey Kayra açık")
            .setContentText("Uyandırma kelimesini bekliyor")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KAYRA • Hey Kayra açık")
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceRunning = false
        handler.removeCallbacksAndMessages(null)
        resetRecognizer()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
