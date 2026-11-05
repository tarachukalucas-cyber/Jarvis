package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var messagesBox: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var composer: EditText
    private lateinit var statusText: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val history = JSONArray()
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }
    private var voiceEnabled = true
    private var accent = Color.rgb(67, 203, 225)
    private var busy = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else addMessage("JARVIS", "Preciso da permissão do microfone para ouvir seus comandos.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceEnabled = prefs.getBoolean("voice", true)
        accent = when (prefs.getString("theme", "cyan")) {
            "purple" -> Color.rgb(173, 125, 255)
            "green" -> Color.rgb(75, 210, 150)
            "amber" -> Color.rgb(255, 190, 90)
            else -> Color.rgb(67, 203, 225)
        }
        tts = TextToSpeech(this, this)
        buildUi()
        addMessage("JARVIS", "Olá. Sou seu assistente. Posso conversar quando a conexão com a IA estiver configurada, abrir aplicativos, pesquisar na web e iniciar seu app de música.")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            setBackgroundColor(Color.rgb(10, 14, 22))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(14))
        }
        val mark = TextView(this).apply {
            text = "✦"
            textSize = 30f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        heading.addView(TextView(this).apply {
            text = "JARVIS"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = .18f
        })
        statusText = TextView(this).apply {
            text = "ASSISTENTE PESSOAL  •  PRONTO"
            textSize = 10f
            setTextColor(Color.rgb(142, 163, 180))
            letterSpacing = .08f
        }
        heading.addView(statusText)
        top.addView(mark, LinearLayout.LayoutParams(dp(42), dp(48)))
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { showSettings() }
        }
        top.addView(settings, LinearLayout.LayoutParams(dp(44), dp(48)))
        root.addView(top)

        val line = View(this).apply { setBackgroundColor(Color.rgb(35, 48, 62)) }
        root.addView(line, LinearLayout.LayoutParams(-1, dp(1)))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(12))
        }
        messagesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messagesBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("Abrir YouTube", "Pesquisar no Google", "Abrir música").forEach { label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.rgb(194, 215, 226))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(Color.rgb(25, 36, 49), dp(18))
                setOnClickListener { runCommand(label) }
            }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginEnd = dp(5)
            suggestions.addView(chip, lp)
        }
        root.addView(suggestions)
        val composeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(6), dp(6))
            background = rounded(Color.rgb(24, 33, 46), dp(24))
        }
        composer = EditText(this).apply {
            hint = "Pergunte ou dê um comando..."
            setHintTextColor(Color.rgb(130, 148, 164))
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 1
            maxLines = 4
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = null
        }
        composeRow.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        val mic = TextView(this).apply {
            text = "🎙"
            textSize = 20f
            gravity = Gravity.CENTER
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoice()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        composeRow.addView(mic, LinearLayout.LayoutParams(dp(42), dp(44)))
        val send = TextView(this).apply {
            text = "➤"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(5, 15, 22))
            background = rounded(accent, dp(18))
            setOnClickListener { sendTyped() }
        }
        composeRow.addView(send, LinearLayout.LayoutParams(dp(42), dp(42)))
        val composerLp = LinearLayout.LayoutParams(-1, -2)
        composerLp.topMargin = dp(10)
        root.addView(composeRow, composerLp)
        setContentView(root)
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun addMessage(who: String, text: String) {
        val isUser = who == "Você"
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(if (isUser) Color.rgb(32, 72, 91) else Color.rgb(25, 34, 47), dp(18))
        }
        bubble.addView(TextView(this).apply {
            this.text = who.uppercase(Locale("pt", "BR"))
            textSize = 10f
            setTextColor(if (isUser) Color.rgb(170, 224, 239) else accent)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
        })
        bubble.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(235, 242, 248))
            setPadding(0, dp(5), 0, 0)
            setTextIsSelectable(true)
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(9))
        }
        row.addView(bubble, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * .82f).toInt(), -2))
        messagesBox.addView(row)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendTyped() {
        val value = composer.text.toString().trim()
        if (value.isNotEmpty() && !busy) {
            composer.text.clear()
            runCommand(value)
        }
    }

    private fun runCommand(raw: String) {
        addMessage("Você", raw)
        val cmd = raw.trim()
        val lower = cmd.lowercase(Locale("pt", "BR"))
        when {
            lower.matches(Regex("^(abra|abrir|abre)\\s+.+")) -> {
                val target = cmd.replaceFirst(Regex("(?i)^(abra|abrir|abre)\\s+"), "").trim()
                addMessage("JARVIS", openTarget(target))
            }
            lower.startsWith("pesquise ") || lower.startsWith("pesquisar ") || lower.startsWith("procure ") || lower.startsWith("google ") -> {
                val query = cmd.substringAfter(" ").trim()
                openWebSearch(query)
                addMessage("JARVIS", "Pesquisando na web: $query")
            }
            lower.startsWith("toque ") || lower.contains("tocar música") || lower.contains("abra música") || lower == "abrir música" -> {
                addMessage("JARVIS", openMusic())
            }
            lower.startsWith("http://") || lower.startsWith("https://") || lower.matches(Regex("^[a-z0-9.-]+\\.[a-z]{2,}(/.*)?$")) -> {
                val address = if (lower.startsWith("http")) cmd else "https://$cmd"
                openUrl(address)
                addMessage("JARVIS", "Abrindo $address")
            }
            lower == "ajuda" -> addMessage("JARVIS", "Você pode dizer: “abra Instagram”, “pesquise receitas”, “abra github.com” ou “abrir música”. Para conversar com a IA, configure o servidor nas opções.")
            else -> askAi(cmd)
        }
    }

    private fun openTarget(target: String): String {
        val normalized = target.lowercase(Locale("pt", "BR")).trim()
        if (normalized.contains(".") || normalized.startsWith("www")) {
            val url = if (normalized.startsWith("http")) target else "https://$target"
            openUrl(url)
            return "Abrindo $url."
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
        val exact = apps.firstOrNull { it.loadLabel(packageManager).toString().equals(target, true) }
        val match = exact ?: apps.firstOrNull {
            it.loadLabel(packageManager).toString().contains(target, true) ||
                target.contains(it.loadLabel(packageManager).toString(), true)
        }
        if (match != null) {
            val launch = packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                return "Abrindo ${match.loadLabel(packageManager)}."
            }
        }
        if (normalized.contains("google")) {
            openUrl("https://www.google.com")
            return "Abrindo o Google."
        }
        return "Não encontrei “$target” entre os aplicativos disponíveis. Confira se está instalado ou tente o nome exibido no celular."
    }

    private fun openMusic(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolve != null) {
            startActivity(intent)
            return "Abrindo seu aplicativo de música."
        }
        openUrl("https://music.youtube.com")
        return "Não encontrei um app de música padrão. Abrindo YouTube Music no navegador."
    }

    private fun openWebSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query)
        try { startActivity(intent) } catch (_: Exception) { openUrl("https://www.google.com/search?q=" + Uri.encode(query)) }
    }

    private fun openUrl(value: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
        catch (_: Exception) { addMessage("JARVIS", "Não consegui abrir esse endereço neste aparelho.") }
    }

    private fun askAi(prompt: String) {
        val endpoint = prefs.getString("ai_endpoint", "")?.trim().orEmpty()
        if (endpoint.isBlank()) {
            addMessage("JARVIS", "Ainda não estou conectado ao modelo de IA. Abra ⚙ Configurações → Conexão de IA e informe o endereço do seu servidor seguro compatível com a API de chat. A chave secreta deve ficar no servidor, nunca dentro do APK.")
            return
        }
        if (!endpoint.startsWith("https://")) {
            addMessage("JARVIS", "Por segurança, o endereço do servidor precisa começar com https://.")
            return
        }
        busy = true
        statusText.text = "IA CONECTANDO..."
        addMessage("JARVIS", "Pensando…")
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = if (endpoint.endsWith("/chat/completions")) endpoint else endpoint.trimEnd('/') + "/v1/chat/completions"
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val messages = JSONArray()
                messages.put(JSONObject().put("role", "system").put("content", "Você é JARVIS, um assistente pessoal útil, claro e seguro. Responda em português do Brasil. Não afirme que executou ações que não executou."))
                val start = maxOf(0, history.length() - 12)
                for (i in start until history.length()) messages.put(history.getJSONObject(i))
                messages.put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject().put("model", "gpt-4o-mini").put("messages", messages).put("temperature", 0.7)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("Servidor respondeu HTTP $code.")
                val answer = JSONObject(responseText).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                history.put(JSONObject().put("role", "user").put("content", prompt))
                history.put(JSONObject().put("role", "assistant").put("content", answer))
                mainHandler.post {
                    removeThinking()
                    addMessage("JARVIS", answer)
                    speak(answer)
                    busy = false
                    statusText.text = "ASSISTENTE PESSOAL  •  ONLINE"
                }
            } catch (e: Exception) {
                mainHandler.post {
                    removeThinking()
                    addMessage("JARVIS", "Não consegui acessar a IA: ${e.message ?: "erro de conexão"}. Confira o endereço HTTPS e se o servidor está ativo.")
                    busy = false
                    statusText.text = "ASSISTENTE PESSOAL  •  OFFLINE"
                }
            } finally { connection?.disconnect() }
        }
    }

    private fun removeThinking() {
        if (messagesBox.childCount > 0) messagesBox.removeViewAt(messagesBox.childCount - 1)
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        panel.addView(TextView(this).apply { text = "Endereço HTTPS do servidor de IA"; textSize = 14f })
        val endpoint = EditText(this).apply {
            hint = "https://seu-servidor.com"
            setSingleLine(true)
            setText(prefs.getString("ai_endpoint", ""))
        }
        panel.addView(endpoint)
        val voice = CheckBox(this).apply { text = "Respostas por voz"; isChecked = voiceEnabled }
        panel.addView(voice)
        panel.addView(TextView(this).apply {
            text = "O servidor deve oferecer uma rota compatível com /v1/chat/completions e guardar a chave do provedor no próprio servidor. Não cole chaves secretas aqui."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(8), 0, 0)
        })
        AlertDialog.Builder(this)
            .setTitle("Configurações do JARVIS")
            .setView(panel)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val value = endpoint.text.toString().trim()
                prefs.edit().putString("ai_endpoint", value).putBoolean("voice", voice.isChecked).apply()
                voiceEnabled = voice.isChecked
                addMessage("JARVIS", if (value.isBlank()) "Conexão de IA não configurada." else "Endereço do servidor salvo. A conexão será testada na próxima mensagem.")
            }.show()
    }

    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("JARVIS", "O reconhecimento de voz não está disponível neste aparelho.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o JARVIS")
        }
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "ESTOU OUVINDO..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { statusText.text = "ASSISTENTE PESSOAL  •  PRONTO"; addMessage("JARVIS", "Não consegui reconhecer. Tente novamente.") }
            override fun onResults(results: Bundle?) {
                statusText.text = "ASSISTENTE PESSOAL  •  PRONTO"
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) runCommand(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        if (::tts.isInitialized && voiceEnabled) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale("pt", "BR"))
    }

    override fun onDestroy() {
        recognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}
