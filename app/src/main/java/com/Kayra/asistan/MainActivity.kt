package com.kayra.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Color(0xFF050B16)
private val Panel = Color(0xFF0D1B2E)
private val Cyan = Color(0xFF39D9FF)
private val WhiteBlue = Color(0xFFD9F5FF)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var recognizedText by mutableStateOf("Seni dinlemeye hazırım.")
    private var responseText by mutableStateOf("Merhaba. Ben KAYRA. Nasıl yardımcı olabilirim?")
    private var listening by mutableStateOf(false)
    private var thinking by mutableStateOf(false)
    private var wakeEnabled by mutableStateOf(false)

    private val wakePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWakeService()
        else {
            responseText = "Hey Kayra için mikrofon izni gerekiyor."
            speak(responseText)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else speak("Mikrofon izni olmadan seni dinleyemem.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        setContent {
            KayraScreen(
                recognizedText = recognizedText,
                responseText = responseText,
                listening = listening,
                thinking = thinking,
                wakeEnabled = wakeEnabled,
                onMicClick = { checkPermissionAndListen() },
                onWakeClick = { toggleWakeService() }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            textToSpeech?.setSpeechRate(0.92f)
        }
    }

    private fun toggleWakeService() {
        if (wakeEnabled) {
            stopService(Intent(this, KayraWakeService::class.java).setAction(KayraWakeService.ACTION_STOP))
            wakeEnabled = false
            responseText = "Hey Kayra bekleme modu kapatıldı."
            speak(responseText)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeService()
        } else {
            wakePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startWakeService() {
        try {
            speechRecognizer?.cancel()
            listening = false
            val serviceIntent = Intent(this, KayraWakeService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            wakeEnabled = true
            responseText = "Hey Kayra bekleme modu başlatıldı. Ekran kapalıyken de deneyebilirsin."
            speak(responseText)
        } catch (e: Exception) {
            wakeEnabled = false
            responseText = "Uyandırma servisi başlatılamadı: ${e.localizedMessage ?: "bilinmeyen hata"}"
            speak(responseText)
        }
    }

    private fun checkPermissionAndListen() {
        if (listening) {
            speechRecognizer?.cancel()
            listening = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            responseText = "Bu telefonda konuşma tanıma hizmeti bulunamadı."
            speak(responseText)
            return
        }
        if (speechRecognizer == null) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    responseText = "Sesi algılayamadım. Tekrar deneyebilirsin."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = words?.firstOrNull().orEmpty()
                if (command.isNotBlank()) {
                    recognizedText = command
                    handleCommand(command)
                } else {
                    responseText = "Seni duyamadım, tekrar deneyebilir misin?"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        listening = true
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            responseText = "Mikrofon başlatılamadı. Tekrar dene."
        }
    }

    private fun handleCommand(rawCommand: String) {
        val command = rawCommand.trim()
        val normalized = command.lowercase(Locale("tr", "TR"))

        when {
            normalized.contains("hatırla") -> {
                val fact = memoryText(command, "hatırla")
                if (fact.isBlank()) answer("Neyi hatırlamamı istersin? Örneğin: En sevdiğim renk mavi, hatırla.")
                else {
                    saveFact(fact)
                    answer("Tamam, bunu hafızama kaydettim: $fact")
                }
            }
            normalized.contains("unut") -> {
                val query = memoryText(command, "unut")
                if (query.isBlank()) answer("Unutmamı istediğin bilgiyi de söyle.")
                else {
                    val removed = forgetFact(query)
                    answer(if (removed) "Tamam, eşleşen bilgiyi hafızamdan çıkardım." else "Bu ifadeyle eşleşen bir kayıt bulamadım.")
                }
            }
            normalized.contains("hafızanda ne var") || normalized.contains("neleri hatırlıyorsun") || normalized.contains("benim hakkımda ne biliyorsun") -> {
                val facts = getSavedFacts()
                answer(if (facts.isEmpty()) "Henüz kaydettiğim bir bilgi yok. Bana bir şey söyleyip hatırlamamı isteyebilirsin." else "Hafızamda şunlar var: ${facts.joinToString(". ")}")
            }
            normalized.contains("saat kaç") -> answer("Saat şu an ${SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())}.")
            normalized == "merhaba" || normalized.startsWith("merhaba kayra") || normalized == "selam" -> answer("Merhaba. Buradayım, nasıl yardımcı olayım?")
            normalized.contains("nasılsın") -> answer("Sistemlerim çalışıyor. Sana yardımcı olmaya hazırım.")
            else -> askAI(command)
        }
    }

    private fun memoryText(text: String, keyword: String): String {
        val index = text.indexOf(keyword, ignoreCase = true)
        if (index < 0) return ""
        val after = text.substring(index + keyword.length).trim(' ', ':', ',', '.', '-')
        if (after.isNotBlank()) return after
        return text.substring(0, index)
            .replace(Regex("(?i)^kayra[,: ]*"), "")
            .trim(' ', ':', ',', '.', '-')
    }

    private fun askAI(command: String) {
        thinking = true
        responseText = "Düşünüyorum..."
        val facts = getSavedFacts()
        val history = getHistory()
        val prompt = buildString {
            append("Sen KAYRA adında Türkçe konuşan, doğal, yardımsever ve Jarvis tarzında bir kişisel asistansın. ")
            append("Kullanıcıya samimi ve kısa cevaplar ver. Bilmediğin şeyi uydurma. Telefonunda doğrudan yapamadığın işlemleri yaptığını söyleme.\n")
            if (facts.isNotEmpty()) append("Kullanıcının özellikle hatırlamamı istediği bilgiler: ${facts.joinToString("; ")}\n")
            if (history.isNotEmpty()) append("Son konuşmalar:\n$history\n")
            append("Kullanıcı: $command\nKAYRA:")
        }
        Thread {
            val result = GeminiService.ask(prompt)
            runOnUiThread {
                thinking = false
                responseText = result
                addHistory(command, result)
                speak(result)
            }
        }.start()
    }

    private fun answer(text: String) {
        responseText = text
        addHistory(recognizedText, text)
        speak(text)
    }

    private fun speak(text: String) {
        if (ttsReady) textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KAYRA_REPLY")
    }

    private fun preferences() = getSharedPreferences("kayra_data", MODE_PRIVATE)

    private fun saveFact(fact: String) {
        val facts = getSavedFacts().toMutableList()
        if (facts.none { it.equals(fact, ignoreCase = true) }) facts.add(fact)
        preferences().edit().putString("facts", JSONArray(facts).toString()).apply()
    }

    private fun getSavedFacts(): List<String> = try {
        val array = JSONArray(preferences().getString("facts", "[]"))
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }

    private fun forgetFact(query: String): Boolean {
        val facts = getSavedFacts()
        val updated = facts.filterNot { it.contains(query, ignoreCase = true) }
        if (updated.size == facts.size) return false
        preferences().edit().putString("facts", JSONArray(updated).toString()).apply()
        return true
    }

    private fun getHistory(): String = try {
        val array = JSONArray(preferences().getString("history", "[]"))
        val start = (array.length() - 10).coerceAtLeast(0)
        (start until array.length()).joinToString("\n") { i ->
            val item = array.getJSONObject(i)
            "Kullanıcı: ${item.optString("user")}\nKAYRA: ${item.optString("assistant")}"
        }
    } catch (_: Exception) { "" }

    private fun addHistory(user: String, assistant: String) {
        try {
            val array = JSONArray(preferences().getString("history", "[]"))
            array.put(org.json.JSONObject().put("user", user).put("assistant", assistant))
            while (array.length() > 50) array.remove(0)
            preferences().edit().putString("history", array.toString()).apply()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun KayraScreen(
    recognizedText: String,
    responseText: String,
    listening: Boolean,
    thinking: Boolean,
    wakeEnabled: Boolean,
    onMicClick: () -> Unit,
    onWakeClick: () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Background, surface = Panel)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text("K A Y R A", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 7.sp)
                Text("PERSONAL AI SYSTEM", color = Color(0xFF7D9DB4), fontSize = 10.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier.size(210.dp).background(
                        Brush.radialGradient(listOf(Color(0xFF123D59), Color(0xFF081526), Background)), CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(180.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(Cyan.copy(alpha = 0.15f), radius = size.minDimension * 0.46f)
                        drawCircle(Cyan.copy(alpha = 0.55f), radius = size.minDimension * 0.39f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                        drawCircle(Cyan.copy(alpha = 0.25f), radius = size.minDimension * 0.29f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                        val bars = 48
                        for (i in 0 until bars) {
                            val angle = (i * 360f / bars) * (Math.PI / 180.0)
                            val inner = size.minDimension * 0.34f
                            val outer = inner + if (listening) (8 + (i % 5) * 2).dp.toPx() else (3 + (i % 3) * 2).dp.toPx()
                            drawLine(Cyan.copy(alpha = if (listening) 0.95f else 0.55f),
                                Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                                Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                                strokeWidth = 2.dp.toPx())
                        }
                    }
                    Text("K", color = WhiteBlue, fontSize = 62.sp, fontWeight = FontWeight.ExtraLight)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    when { listening -> "SENİ DİNLİYORUM"; thinking -> "DÜŞÜNÜYORUM"; else -> "SİSTEM HAZIR" },
                    color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(26.dp))
                InfoPanel("INPUT / SESLİ KOMUT", recognizedText)
                Spacer(Modifier.height(12.dp))
                InfoPanel("KAYRA / YANIT", responseText)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onMicClick,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Background)
                ) {
                    Text(if (listening) "DİNLEMEYİ DURDUR" else "◉  KAYRA'YI ETKİNLEŞTİR", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onWakeClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                ) {
                    Text(
                        if (wakeEnabled) "HEY KAYRA BEKLEME MODUNU KAPAT"
                        else "◉  HEY KAYRA MODUNU AÇ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (wakeEnabled) "UYANDIRMA KELİMESİ AKTİF • SERVİS ÇALIŞIYOR"
                    else "Ekran kapalıyken uyandırmak için Hey Kayra modunu aç",
                    color = Color(0xFF7D9DB4),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Text("VOICE  •  MEMORY  •  ARTIFICIAL INTELLIGENCE", color = Color(0xFF607C91), fontSize = 9.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Text(title, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = WhiteBlue, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
