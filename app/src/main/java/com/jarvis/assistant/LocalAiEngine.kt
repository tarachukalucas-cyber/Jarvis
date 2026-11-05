package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.ConversationWindow
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.tools.Tool
import io.aatricks.llmedge.tools.ToolKind
import io.aatricks.llmedge.tools.ToolParameter
import io.aatricks.llmedge.tools.ToolParameterType
import io.aatricks.llmedge.tools.ToolPolicies
import io.aatricks.llmedge.tools.ToolResult
import io.aatricks.llmedge.tools.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Local GGUF model and an allow-listed set of harmless Android actions.
 * No arbitrary shell execution, messaging, calling, or destructive device actions.
 */
class LocalAiEngine(context: Context, scope: CoroutineScope) : AutoCloseable {
    private val app = context.applicationContext
    private val edge = LLMEdge.create(app, scope)
    private val model = ModelSpec.huggingFace(
        repoId = "ggml-org/Qwen3-1.7B-GGUF",
        filename = "Qwen3-1.7B-Q4_K_M.gguf",
    )
    private val options = TextModelOptions(
        contextSize = 2048L,
        numThreads = 2,
        generationThreads = 2,
    )
    @Volatile private var agent: io.aatricks.llmedge.tools.ToolAgent? = null

    val isReady: Boolean get() = agent != null

    suspend fun downloadAndPrepare() {
        edge.models.prefetch(model)
        edge.text.prepare(model, options)
        val configuredAgent = edge.text.toolAgent(
            tools = createTools() + AndroidActionTools.create(app),
            model = model,
            memory = ConversationWindow(maxTurns = 8, maxTokens = 3072),
            systemPrompt = """
                Você é JARVIS, um assistente pessoal em português do Brasil.
                Converse naturalmente e mantenha o contexto das mensagens anteriores.
                Quando o usuário pedir para abrir um aplicativo, site, pesquisar no Google ou abrir música,
                use a ferramenta correspondente. Não diga que realizou uma ação sem receber sucesso da ferramenta.
                Só use as ferramentas disponíveis. Não tente executar comandos de terminal ou ações fora dessa lista.
            """.trimIndent(),
            options = options,
            policy = ToolPolicies.ALLOW_ALL,
        )
        configuredAgent.prepare()
        agent = configuredAgent
    }

    suspend fun reply(message: String, recentContext: String = ""): String {
        val active = agent ?: error("O modelo local ainda não foi baixado.")
        val contextualPrompt = if (recentContext.isBlank()) {
            message
        } else {
            """
            Contexto recente da conversa (use apenas para manter continuidade; não repita tudo):
            $recentContext

            Nova mensagem do usuário:
            $message
            """.trimIndent()
        }
        return active.reply(contextualPrompt, maxSteps = 5, maxTokens = 420).text
    }

    private fun createTools(): List<Tool> = listOf(
        Tool(
            name = "open_app",
            description = "Abre um aplicativo instalado no Android pelo nome que aparece na tela inicial.",
            kind = ToolKind.ACTION,
            schema = ToolSchema(mapOf(
                "app_name" to ToolParameter(ToolParameterType.STRING, "Nome visível do aplicativo."),
            )),
            handler = { args ->
                val name = args["app_name"]?.jsonPrimitive?.contentOrNull
                if (name.isNullOrBlank()) ToolResult.error("Nome do aplicativo ausente.")
                else withContext(Dispatchers.Main.immediate) { openInstalledApp(name) }
            },
        ),
        Tool(
            name = "open_url",
            description = "Abre um site no navegador padrão. Recebe uma URL completa.",
            kind = ToolKind.ACTION,
            schema = ToolSchema(mapOf(
                "url" to ToolParameter(ToolParameterType.STRING, "Endereço HTTPS do site."),
            )),
            handler = { args ->
                val value = args["url"]?.jsonPrimitive?.contentOrNull
                if (value.isNullOrBlank()) ToolResult.error("Endereço ausente.")
                else withContext(Dispatchers.Main.immediate) { openWebAddress(value) }
            },
        ),
        Tool(
            name = "google_search",
            description = "Pesquisa uma frase ou pergunta no Google.",
            kind = ToolKind.ACTION,
            schema = ToolSchema(mapOf(
                "query" to ToolParameter(ToolParameterType.STRING, "Texto a pesquisar."),
            )),
            handler = { args ->
                val query = args["query"]?.jsonPrimitive?.contentOrNull
                if (query.isNullOrBlank()) ToolResult.error("Pesquisa vazia.")
                else withContext(Dispatchers.Main.immediate) { searchGoogle(query) }
            },
        ),
        Tool(
            name = "open_music",
            description = "Abre o aplicativo de música padrão do Android ou YouTube Music no navegador.",
            kind = ToolKind.ACTION,
            schema = ToolSchema(),
            handler = { withContext(Dispatchers.Main.immediate) { launchMusic() } },
        ),
    )

    private fun openInstalledApp(requested: String): ToolResult {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = app.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
        val exact = apps.firstOrNull { it.loadLabel(app.packageManager).toString().equals(requested, true) }
        val match = exact ?: apps.firstOrNull {
            it.loadLabel(app.packageManager).toString().contains(requested, true)
        }
        val info = match ?: return ToolResult.error("Não encontrei o aplicativo '$requested' instalado.")
        val launch = app.packageManager.getLaunchIntentForPackage(info.activityInfo.packageName)
            ?: return ToolResult.error("O aplicativo '${info.loadLabel(app.packageManager)}' não permite inicialização externa.")
        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launch)
            ToolResult.success("Aplicativo ${info.loadLabel(app.packageManager)} aberto.")
        } catch (e: Exception) {
            ToolResult.error("Não consegui abrir '$requested': ${e.message ?: "erro do Android"}.")
        }
    }

    private fun openWebAddress(value: String): ToolResult {
        val uri = Uri.parse(value)
        if (uri.scheme != "https" && uri.scheme != "http") return ToolResult.error("Só posso abrir endereços HTTP ou HTTPS.")
        return try {
            app.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult.success("Site aberto: $value")
        } catch (e: Exception) {
            ToolResult.error("Não consegui abrir o site: ${e.message ?: "erro do Android"}.")
        }
    }

    private fun searchGoogle(query: String): ToolResult =
        openWebAddress("https://www.google.com/search?q=" + Uri.encode(query))

    private fun launchMusic(): ToolResult {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        return try {
            val resolved = app.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                ToolResult.success("Aplicativo de música aberto.")
            } else {
                openWebAddress("https://music.youtube.com")
            }
        } catch (e: Exception) {
            ToolResult.error("Não consegui abrir música: ${e.message ?: "erro do Android"}.")
        }
    }

    override fun close() {
        agent = null
        edge.close()
    }
}
