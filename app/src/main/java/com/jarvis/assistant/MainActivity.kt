package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var transcript: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private lateinit var page: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private var speechRecognizer: SpeechRecognizer? = null
    private val messages = mutableListOf<String>()
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }
    private var accentColor = Color.rgb(0, 210, 240)
    private var backgroundColor = Color.rgb(5, 11, 18)
    private var voiceEnabled = true
    private var compactMode = false
    private var historyLimit = 100
    private var launchableApps: List<Pair<String, String>>? = null

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listenNow() else addLine("JARVIS", "A permissão do microfone não foi concedida.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
        tts = TextToSpeech(this, this)
        buildInterface()
        addLine("JARVIS", "Olá. Sou o JARVIS. Posso conversar por texto ou voz e abrir aplicativos por comando.")
    }

    private fun loadSettings() {
        accentColor = when (prefs.getString("theme", "cyan")) {
            "purple" -> Color.rgb(174, 112, 255)
            "green" -> Color.rgb(68, 220, 150)
            "amber" -> Color.rgb(255, 185, 75)
            else -> Color.rgb(0, 210, 240)
        }
        backgroundColor = if (prefs.getBoolean("dark", true)) Color.rgb(5, 11, 18) else Color.rgb(238, 244, 248)
        voiceEnabled = prefs.getBoolean("voice", true)
        compactMode = prefs.getBoolean("compact", false)
        historyLimit = if (prefs.getBoolean("lite", false)) 40 else 100
    }

    private fun buildInterface() {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 16)
        }
        titleView = TextView(this).apply {
            text = "J A R V I S"
            textSize = 27f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        subtitleView = TextView(this).apply {
            text = "ASSISTENTE PESSOAL"
            textSize = 11f
            gravity = Gravity.CENTER
        }
        transcript = TextView(this).apply {
            textSize = if (compactMode) 14f else 16f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
        }
        transcriptScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(transcript)
        }
        input = EditText(this).apply {
            hint = "Digite um comando..."
            setSingleLine(false)
            maxLines = if (compactMode) 2 else 4
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
        val customize = Button(this).apply {
            text = "PERSONALIZAR"
            setOnClickListener { showCustomization() }
        }
        val optimize = Button(this).apply {
            text = "OTIMIZAÇÃO"
            setOnClickListener { showOptimization() }
        }

        page.addView(titleView)
        page.addView(subtitleView)
        page.addView(transcriptScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = 12
            bottomMargin = 10
        })
        page.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(send, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(mic, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(actions)
        val settingsActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        settingsActions.addView(customize, LinearLayout.LayoutParams(0, -2, 1f))
        settingsActions.addView(optimize, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(settingsActions)
        setContentView(page)
        applyAppearance()
    }

    private fun applyAppearance() {
        val dark = prefs.getBoolean("dark", true)
        val textColor = if (dark) Color.rgb(229, 244, 247) else Color.rgb(25, 42, 52)
        val panelColor = if (dark) Color.rgb(20, 35, 45) else Color.rgb(220, 231, 237)
        page.setBackgroundColor(backgroundColor)
        titleView.setTextColor(accentColor)
        subtitleView.setTextColor(if (dark) Color.rgb(139, 170, 181) else Color.rgb(75, 100, 112))
        transcript.setTextColor(textColor)
        transcript.setBackgroundColor(panelColor)
        input.setTextColor(textColor)
        input.setHintTextColor(if (dark) Color.rgb(121, 145, 154) else Color.rgb(105, 123, 132))
        input.setBackgroundColor(panelColor)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = if (dark) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun showCustomization() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
        }
        content.addView(TextView(this).apply { text = "Tema de cores"; textSize = 16f })
        val themes = listOf("Ciano" to "cyan", "Roxo" to "purple", "Verde" to "green", "Âmbar" to "amber")
        val themeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        themes.forEach { (label, key) ->
            themeGroup.addView(RadioButton(this).apply {
                text = label
                tag = key
                isChecked = prefs.getString("theme", "cyan") == key
            })
        }
        content.addView(themeGroup)
        val darkCheck = CheckBox(this).apply {
            text = "Interface escura"
            isChecked = prefs.getBoolean("dark", true)
        }
        val voiceCheck = CheckBox(this).apply {
            text = "Responder usando voz"
            isChecked = voiceEnabled
        }
        val compactCheck = CheckBox(this).apply {
            text = "Interface compacta"
            isChecked = compactMode
        }
        content.addView(darkCheck)
        content.addView(voiceCheck)
        content.addView(compactCheck)

        AlertDialog.Builder(this)
            .setTitle("Personalizar JARVIS")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val selected = themeGroup.findViewById<RadioButton>(themeGroup.checkedRadioButtonId)
                prefs.edit()
                    .putString("theme", selected?.tag as? String ?: "cyan")
                    .putBoolean("dark", darkCheck.isChecked)
                    .putBoolean("voice", voiceCheck.isChecked)
                    .putBoolean("compact", compactCheck.isChecked)
                    .apply()
                loadSettings()
                input.maxLines = if (compactMode) 2 else 4
                transcript.textSize = if (compactMode) 14f else 16f
                applyAppearance()
                addLine("JARVIS", "Preferências atualizadas.")
            }
            .show()
    }

    private fun showOptimization() {
        val liteCheck = CheckBox(this).apply {
            text = "Ativar modo leve (histórico menor e interface compacta)"
            isChecked = prefs.getBoolean("lite", false)
        }
        AlertDialog.Builder(this)
            .setTitle("Otimização")
            .setMessage("O modo leve limita o histórico mantido na tela para reduzir o trabalho da interface. Ele não altera nem promete liberar a memória do Android.")
            .setView(liteCheck)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Limpar conversa") { _, _ ->
                messages.clear()
                renderMessages()
                addLine("JARVIS", "Conversa limpa.")
            }
            .setPositiveButton("Aplicar") { _, _ ->
                prefs.edit().putBoolean("lite", liteCheck.isChecked).apply()
                historyLimit = if (liteCheck.isChecked) 40 else 100
                if (liteCheck.isChecked) {
                    compactMode = true
                    prefs.edit().putBoolean("compact", true).apply()
                    input.maxLines = 2
                    transcript.textSize = 14f
                }
                while (messages.size > historyLimit) messages.removeAt(0)
                renderMessages()
                addLine("JARVIS", if (liteCheck.isChecked) "Modo leve ativado." else "Modo leve desativado.")
            }
            .show()
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
                "Posso conversar por texto ou voz, abrir aplicativos e salvar suas preferências. Experimente: abra YouTube."
            else ->
                "Entendi: \"$raw\". Minha conexão com uma IA generativa ainda será adicionada. Experimente: abra YouTube."
        }
        addLine("JARVIS", response)
        speak(response)
    }

    private fun openApp(requested: String): String {
        val apps = launchableApps ?: run {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            launchableApps = packageManager.queryIntentActivities(launcherIntent, 0).map {
                it.loadLabel(packageManager).toString() to it.activityInfo.packageName
            }.distinctBy { it.second }
            launchableApps.orEmpty()
        }
        val match = apps.firstOrNull { it.first.contains(requested, ignoreCase = true) }
        if (match != null) {
            val launch = packageManager.getLaunchIntentForPackage(match.second)
            if (launch != null) {
                startActivity(launch)
                return "Abrindo ${match.first}."
            }
        }
        return "Não encontrei um aplicativo chamado $requested."
    }

    private fun addLine(who: String, message: String) {
        messages.add("$who: $message")
        while (messages.size > historyLimit) messages.removeAt(0)
        renderMessages()
    }

    private fun renderMessages() {
        transcript.text = messages.joinToString("\n\n")
        transcriptScroll.post { transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun speak(message: String) {
        if (::tts.isInitialized && voiceEnabled) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                voiceEnabled = false
                prefs.edit().putBoolean("voice", false).apply()
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
