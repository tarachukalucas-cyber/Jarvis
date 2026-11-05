package com.jarvis.assistant
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class JarvisMaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("ai_update_url", "")?.trim().orEmpty()
        if (endpoint.isBlank()) return@withContext Result.success()
        if (!endpoint.startsWith("https://")) return@withContext Result.failure()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code == 408 || code == 429 || code >= 500) return@withContext Result.retry()
            if (code !in 200..299) return@withContext Result.failure()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val prompt = json.optString("system_prompt").trim()
            val version = json.optString("version", "unknown")
            if (prompt.length > 12000) return@withContext Result.failure()
            if (prompt.isNotBlank()) prefs.edit()
                .putString("remote_system_prompt", prompt)
                .putString("remote_profile_version", version)
                .putLong("remote_profile_updated_at", System.currentTimeMillis())
                .apply()
            Result.success()
        } catch (_: Exception) { Result.retry() }
        finally { connection?.disconnect() }
    }
}
