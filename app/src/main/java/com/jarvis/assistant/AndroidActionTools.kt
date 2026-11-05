package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import io.aatricks.llmedge.tools.Tool
import io.aatricks.llmedge.tools.ToolKind
import io.aatricks.llmedge.tools.ToolParameter
import io.aatricks.llmedge.tools.ToolParameterType
import io.aatricks.llmedge.tools.ToolResult
import io.aatricks.llmedge.tools.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Android integrations inspired by OpenJarvis' pluggable tool layer.
 *
 * Every operation opens a visible Android/system screen. The app does not
 * silently change protected settings or execute arbitrary shell commands.
 */
object AndroidActionTools {
    fun create(context: Context): List<Tool> {
        val app = context.applicationContext
        return listOf(
            Tool(
                name = "open_android_settings",
                description = "Abre uma tela de configurações do Android. Seções aceitas: geral, wifi, bluetooth, som, tela ou bateria.",
                kind = ToolKind.ACTION,
                schema = ToolSchema(mapOf(
                    "section" to ToolParameter(ToolParameterType.STRING, "Seção: geral, wifi, bluetooth, som, tela ou bateria.")
                )),
                handler = { args ->
                    val section = args["section"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim()
                    val action = when (section) {
                        "wifi", "wi-fi", "internet" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "som", "áudio", "audio" -> Settings.ACTION_SOUND_SETTINGS
                        "tela", "display" -> Settings.ACTION_DISPLAY_SETTINGS
                        "bateria", "energia" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                        "geral", "configurações", "configuracoes", null, "" -> Settings.ACTION_SETTINGS
                        else -> null
                    }
                    if (action == null) ToolResult.error("Seção não reconhecida. Use geral, wifi, bluetooth, som, tela ou bateria.")
                    else withContext(Dispatchers.Main.immediate) {
                        launchSystemScreen(app, Intent(action), "Configurações abertas.")
                    }
                }
            ),
            Tool(
                name = "start_timer",
                description = "Abre a tela do relógio Android para configurar um temporizador. O usuário confirma a ação no sistema.",
                kind = ToolKind.ACTION,
                schema = ToolSchema(mapOf(
                    "seconds" to ToolParameter(ToolParameterType.STRING, "Duração em segundos, entre 1 e 86400."),
                    "label" to ToolParameter(ToolParameterType.STRING, "Nome curto do temporizador.")
                )),
                handler = { args ->
                    val seconds = args["seconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    val label = args["label"]?.jsonPrimitive?.contentOrNull?.take(40) ?: "JARVIS"
                    if (seconds == null || seconds !in 1..86400) ToolResult.error("Duração inválida. Informe segundos entre 1 e 86400.")
                    else withContext(Dispatchers.Main.immediate) {
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        launchSystemScreen(app, intent, "Temporizador preparado. Confirme no aplicativo Relógio.")
                    }
                }
            ),
            Tool(
                name = "create_calendar_event",
                description = "Preenche a tela de novo evento do calendário. O usuário revisa e salva no próprio aplicativo.",
                kind = ToolKind.ACTION,
                schema = ToolSchema(mapOf(
                    "title" to ToolParameter(ToolParameterType.STRING, "Título do evento."),
                    "start_time_millis" to ToolParameter(ToolParameterType.STRING, "Início como timestamp Unix em milissegundos."),
                    "end_time_millis" to ToolParameter(ToolParameterType.STRING, "Fim como timestamp Unix em milissegundos.")
                )),
                handler = { args ->
                    val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val start = args["start_time_millis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val end = args["end_time_millis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    if (title.isBlank() || start == null || end == null || end <= start || start <= System.currentTimeMillis()) {
                        ToolResult.error("Evento inválido. Informe título, início futuro e fim posterior ao início.")
                    } else withContext(Dispatchers.Main.immediate) {
                        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                            .putExtra(CalendarContract.Events.TITLE, title.take(120))
                        launchSystemScreen(app, intent, "Evento preenchido. Revise e salve no calendário.")
                    }
                }
            )
        )
    }

    private fun launchSystemScreen(context: Context, intent: Intent, success: String): ToolResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.success(success)
        } catch (e: Exception) {
            ToolResult.error("O Android não disponibilizou essa tela: ${e.message ?: "erro do sistema"}.")
        }
    }
}
