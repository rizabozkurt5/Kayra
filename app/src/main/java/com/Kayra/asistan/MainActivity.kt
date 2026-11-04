
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Cyan = Color(0xFF00DFFF)
private val Background = Color(0xFF050C16)
private val Panel = Color(0xFF101D2B)

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var listening = mutableStateOf(false)
    private var status = mutableStateOf("SİSTEM HAZIR")
    private var recognizedText = mutableStateOf("Merhaba, ben Kayra.")
    private var responseText = mutableStateOf("Komutunu bekliyorum.")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                status.value = "MİKROFON İZNİ GEREKLİ"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts.language = Locale("tr", "TR")
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.value = "DİNLİYOR..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening.value = false
                    status.value = "KOMUT İŞLENİYOR"
                }

                override fun onError(error: Int) {
                    listening.value = false
                    status.value = "TEKRAR DENE"
                }

                override fun onResults(results: Bundle?) {
                    listening.value = false

                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val command = words?.firstOrNull() ?: ""
                    recognizedText.value = command

                    if (command.isBlank()) {
                        status.value = "KOMUT ALGILANAMADI"
                        return
                    }

                    val answer = handleCommand(command)

                    if (answer != null) {
                        responseText.value = answer
                        status.value = "SİSTEM HAZIR"
                        speak(answer)
                    } else {
                        askGemini(command)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val words = partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    if (!words.isNullOrEmpty()) {
                        recognizedText.value = words[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            status.value = "SES TANIMA DESTEKLENMİYOR"
        }

        setContent {
            KayraDashboard(
                status = status.value,
                listening = listening.value,
                recognizedText = recognizedText.value,
                responseText = responseText.value,
                onMicClick = { onMicClick() }
            )
        }
    }

    private fun onMicClick() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.value = "SES TANIMA KULLANILAMIYOR"
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            listening.value = true
            status.value = "BAĞLANIYOR..."
            recognizedText.value = "Seni dinliyorum..."

            speechRecognizer.startListening(intent)

        } catch (e: Exception) {
            listening.value = false
            status.value = "BAŞLATMA HATASI"
        }
    }

    private fun askGemini(command: String) {
        status.value = "GEMINI'YE BAĞLANIYOR..."
        responseText.value = "Düşünüyorum..."

        Thread {
            val answer = GeminiService.ask(command)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                responseText.value = answer
                status.value = "SİSTEM HAZIR"
                speak(answer)
            }
        }.start()
    }

    private fun speak(answer: String) {
        tts.speak(
            answer,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "kayra_response"
        )
    }

    private fun handleCommand(command: String): String? {
        val text = command.lowercase(Locale("tr", "TR"))

        return when {
            text.contains("merhaba") || text.contains("selam") ->
                "Merhaba! Ben Kayra. Sana nasıl yardımcı olabilirim?"

            text.contains("adın") || text.contains("kimsin") ->
                "Ben Kayra, kişisel yapay zeka asistanınım."

            text.contains("saat kaç") ->
                "Şu an saat " +
                    SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())

            text.contains("nasılsın") ->
                "Teşekkür ederim. Sistemlerim hazır ve seni dinliyorum."

            else -> null
        }
    }

    override fun onDestroy() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        super.onDestroy()
    }
}

@Composable
fun KayraDashboard(
    status: String,
    listening: Boolean,
    recognizedText: String,
    responseText: String,
    onMicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            "K A Y R A",
            color = Cyan,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "PERSONAL AI ASSISTANT",
            color = Color.Gray,
            fontSize = 10.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusItem("SYSTEM", "ONLINE")
            StatusItem("AI CORE", "READY")
            StatusItem("VOICE", if (listening) "ACTIVE" else "STANDBY")
        }

        Spacer(Modifier.height(45.dp))

        Box(
            modifier = Modifier
                .size(250.dp)
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Cyan.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )

                drawCircle(
                    color = Cyan.copy(alpha = 0.45f),
                    radius = size.minDimension * 0.46f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
                )

                drawCircle(
                    color = Cyan.copy(alpha = 0.2f),
                    radius = size.minDimension * 0.35f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(125.dp)
                    .background(Panel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (listening) "DİNLEME" else "KAYRA",
                    color = Cyan,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            status,
            color = Cyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(25.dp))

        InfoPanel("SENİN SÖYLEDİĞİN", recognizedText)

        Spacer(Modifier.height(12.dp))

        InfoPanel("KAYRA'NIN CEVABI", responseText)

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onMicClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Cyan,
                contentColor = Background
            )
        ) {
            Text(
                if (listening) "DİNLENİYOR..." else "MİKROFONU AÇ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(15.dp))
    }
}

@Composable
fun StatusItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .background(Panel, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.Gray, fontSize = 9.sp)
        Text(value, color = Cyan, fontSize = 11.sp)
    }
}

@Composable
fun InfoPanel(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(15.dp)
    ) {
        Text(
            title,
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(7.dp))

        Text(
            value,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
