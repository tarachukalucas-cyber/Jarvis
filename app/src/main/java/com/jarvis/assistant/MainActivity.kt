package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listenNow() else addLine("JARVIS", "A permissão do microfone não foi concedida.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildInterface()
        addLine("JARVIS", "Olá. Sou o JARVIS. Posso conversar por texto ou voz e abrir aplicativos por comando.")
    }

    private fun buildInterface() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 20)
            setBackgroundColor(android.graphics.Color.rgb(5, 11, 18))
        }
        val title = TextView(this).apply {
            text = "J A R V I S"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(0, 210, 240))
        }
        val subtitle = TextView(this).apply {
            text = "ASSISTENTE PESSOAL"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF8BAAB5.toInt())
        }
        transcript = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFE5F4F7.toInt())
            setPadding(16, 16, 16, 16)
        }
        val scroll = ScrollView(this).apply { addView(transcript) }
        input = EditText(this).apply {
            hint = "Digite um comando..."
            setHintTextColor(0xFF79919A.toInt())
            setTextColor(-1)
            setSingleLine(false)
            setBackgroundColor(0xFF14232D.toInt())
            setPadding(14, 12, 14, 12)
        }
        val send = Button(this).apply {
            text = "ENVIAR"
            setOnClickListener { submitText() }
        }
        val mic = Button(this).apply {
            text = "🎙 FALAR"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) listenNow()
                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        page.addView(title)
        page.addView(subtitle)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(send, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(mic, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(actions)
        setContentView(page)
    }

    private fun submitText() {
        val command = input.text.toString().trim()
        if (command.isEmpty()) return
        input.text.clear()
        addLine("Você", command)
        handleCommand(command)
    }

    private fun listenNow() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addLine("JARVIS", "O reconhecimento de voz não está disponível neste aparelho.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o JARVIS")
        }
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { addLine("JARVIS", "Não consegui reconhecer a fala. Tente novamente.") }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    addLine("Você", spoken)
                    handleCommand(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale("pt", "BR")).trim()
        val response = when {
            command.startsWith("abra ") || command.startsWith("abrir ") || command.startsWith("abre ") -> {
                val appName = command.substringAfter(" ").trim()
                openApp(appName)
            }
            command == "ajuda" || command.contains("o que você faz") ->
                "Por enquanto posso conversar e abrir aplicativos instalados. Experimente: abra YouTube."
            else ->
                "Entendi: \"$raw\". Minha conexão com uma IA generativa será adicionada em uma próxima versão. Experimente: abra YouTube."
        }
        addLine("JARVIS", response)
        speak(response)
    }

    private fun openApp(requested: String): String {
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = installed.firstOrNull {
            packageManager.getApplicationLabel(it).toString().contains(requested, ignoreCase = true)
        }
        if (match != null) {
            val launch = packageManager.getLaunchIntentForPackage(match.packageName)
            if (launch != null) {
                startActivity(launch)
                return "Abrindo ${packageManager.getApplicationLabel(match)}."
            }
        }
        return "Não encontrei um aplicativo chamado $requested."
    }

    private fun addLine(who: String, message: String) {
        transcript.append("$who: $message\n\n")
    }

    private fun speak(message: String) {
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
