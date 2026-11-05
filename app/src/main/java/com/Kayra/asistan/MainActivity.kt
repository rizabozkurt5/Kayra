
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Cyan = Color(0xFF00DFFF)
private val Blue = Color(0xFF087BFF)
private val Background = Color(0xFF030914)
private val Panel = Color(0xFF0B1A2B)
private val WhiteBlue = Color(0xFFB9F5FF)

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var listening = mutableStateOf(false)
    private var status = mutableStateOf("SİSTEM HAZIR")
    private var recognizedText = mutableStateOf("Merhaba, ben Kayra.")
    private var responseText = mutableStateOf("Komutunu bekliyorum.")

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
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

        val turkish = Locale("tr", "TR")
        val voices = tts.voices

        val maleVoice = voices?.firstOrNull {
            it.locale.language == "tr" &&
            it.name.contains("male", ignoreCase = true)
        }

        if (maleVoice != null) {
            tts.voice = maleVoice
        } else {
            tts.language = turkish
        }

        tts.setSpeechRate(0.92f)
        tts.setPitch(0.85f)
    }
}

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {

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

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {
                        val words = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        if (!words.isNullOrEmpty()) {
                            recognizedText.value = words[0]
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )
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

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            startListening()
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "tr-TR"
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )
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
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

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
            text.contains("merhaba") ||
                    text.contains("selam") ->
                "Merhaba! Ben Kayra. Sana nasıl yardımcı olabilirim?"

            text.contains("adın") ||
                    text.contains("kimsin") ->
                "Ben Kayra, kişisel yapay zeka asistanınım."

            text.contains("saat kaç") ->
                "Şu an saat " +
                        SimpleDateFormat(
                            "HH:mm",
                            Locale("tr", "TR")
                        ).format(Date())

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
    val infiniteTransition =
        rememberInfiniteTransition(label = "kayra")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 12000,
                easing = LinearEasing
            )
        ),
        label = "rotation"
    )

    val reverseRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 9000,
                easing = LinearEasing
            )
        ),
        label = "reverse"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val wave by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(35.dp))

        Text(
            "K A Y R A",
            color = Cyan,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp
        )

        Text(
            "ADVANCED PERSONAL AI SYSTEM",
            color = WhiteBlue.copy(alpha = 0.65f),
            fontSize = 9.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusItem("SYSTEM", "ONLINE")
            StatusItem("AI CORE", "ACTIVE")
            StatusItem(
                "VOICE",
                if (listening) "ACTIVE" else "STANDBY"
            )
        }

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {

            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(
                    size.width / 2f,
                    size.height / 2f
                )

                val radius = size.minDimension * 0.43f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Cyan.copy(alpha = 0.18f),
                            Blue.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 1.5f
                    )
                )

                rotate(rotation, center) {
                    drawCircle(
                        color = Cyan.copy(alpha = 0.75f),
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    for (i in 0 until 48) {
                        rotate(i * 7.5f, center) {
                            drawLine(
                                color = Cyan.copy(
                                    alpha = if (i % 4 == 0) 0.9f else 0.3f
                                ),
                                start = androidx.compose.ui.geometry.Offset(
                                    center.x,
                                    center.y - radius
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    center.x,
                                    center.y - radius +
                                            if (i % 4 == 0) {
                                                13.dp.toPx()
                                            } else {
                                                5.dp.toPx()
                                            }
                                ),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    drawArc(
                        color = Cyan,
                        startAngle = 0f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - radius - 8.dp.toPx(),
                            center.y - radius - 8.dp.toPx()
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            (radius + 8.dp.toPx()) * 2,
                            (radius + 8.dp.toPx()) * 2
                        ),
                        style = Stroke(4.dp.toPx())
                    )
                }

                rotate(reverseRotation, center) {
                    drawCircle(
                        color = Blue.copy(alpha = 0.8f),
                        radius = radius * 0.76f,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(12f, 10f)
                            )
                        )
                    )
                }

                drawCircle(
                    color = Cyan.copy(alpha = 0.18f),
                    radius = radius * 0.55f * pulse
                )

                drawCircle(
                    color = Cyan.copy(alpha = 0.9f),
                    radius = radius * 0.48f,
                    style = Stroke(2.dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(125.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF174B75),
                                    Color(0xFF09243F),
                                    Background
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "K",
                        color = Cyan,
                        fontSize = 66.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    if (listening) "LISTENING" else "KAYRA CORE",
                    color = Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
        }

        Text(
            status,
            color = Cyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(14.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
        ) {
            val barCount = 28
            val barWidth = 4.dp.toPx()
            val gap = 4.dp.toPx()
            val totalWidth = barCount * (barWidth + gap)
            val startX = (size.width - totalWidth) / 2f

            for (i in 0 until barCount) {
                val factor = if (listening) {
                    0.2f + ((i * 7) % 10) / 10f * wave
                } else {
                    0.12f
                }

                val barHeight = size.height * factor
                val x = startX + i * (barWidth + gap)

                drawLine(
                    color = Cyan.copy(
                        alpha = if (listening) 0.9f else 0.35f
                    ),
                    start = androidx.compose.ui.geometry.Offset(
                        x,
                        (size.height - barHeight) / 2f
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        x,
                        (size.height + barHeight) / 2f
                    ),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        InfoPanel(
            "INPUT / SESLİ KOMUT",
            recognizedText
        )

        Spacer(Modifier.height(12.dp))

        InfoPanel(
            "KAYRA / YANIT",
            responseText
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onMicClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Cyan,
                contentColor = Background
            )
        ) {
            Text(
                if (listening) "DİNLENİYOR..."
                else "◉  KAYRA'YI ETKİNLEŞTİR",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(25.dp))

        Text(
            "KAYRA ARTIFICIAL INTELLIGENCE",
            color = Color.Gray,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun StatusItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .background(
                Panel,
                RoundedCornerShape(10.dp)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color.Gray,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            value,
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoPanel(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Panel,
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = value,
            color = WhiteBlue,
            fontSize = 14.sp
        )
    }
}
