package com.example.quotebeast

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val DEFAULT_PROMPT =
    "Write ONE grounded motivational sentence. Maximum 11 words. Honest, no fluff. Output ONLY the sentence. Do not show thinking."

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QuoteBeastScreen() }
    }
}

@Composable
fun QuoteBeastScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("quote_beast", 0)
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var prompt by remember { mutableStateOf(prefs.getString("prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT) }
    var model by remember { mutableStateOf(prefs.getString("model", "qwen/qwen3.6-27b") ?: "qwen/qwen3.6-27b") }
    var temperature by remember { mutableStateOf(prefs.getString("temperature", "0.7") ?: "0.7") }
    var status by remember { mutableStateOf("Copy an X post, then tap Generate") }
    var result by remember { mutableStateOf(prefs.getString("last_result", "") ?: "") }
    var loading by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "last_result") {
                result = prefs.getString("last_result", "") ?: ""
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun save() {
        prefs.edit()
            .putString("api_key", apiKey)
            .putString("prompt", prompt)
            .putString("model", model)
            .putString("temperature", temperature)
            .apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Quote Beast", fontSize = 28.sp, color = Color.Black)
        Text("Boost mode. Uses clipboard.", fontSize = 16.sp, color = Color.Black)

        Button(
            onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)
                val post = clip.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                save()

                if (apiKey.isBlank()) {
                    status = "Paste a Groq key in Settings"
                    showSettings = true
                    return@Button
                }

                val lastOut = prefs.getString("last_result", "") ?: ""
                val source = if (post.isNotBlank() && post != lastOut) post else ""

                loading = true
                status = if (source.isBlank()) "Using default prompt..." else "Generating..."
                scope.launch {
                    val out = generateBoost(apiKey, source, prompt, model, temperature)
                    loading = false
                    if (out != null) {
                        prefs.edit().putString("last_result", out).apply()
                        result = out
                        status = if (source.isBlank()) "Copied (default prompt)" else "Copied"
                        clip.setPrimaryClip(ClipData.newPlainText("quote", out))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    } else {
                        status = "Groq failed. Check key / model / network."
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Working..." else "Generate from clipboard")
        }

        OutlinedButton(
            onClick = {
                save()
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                    status = "Allow Display over other apps, then tap again"
                } else {
                    context.startForegroundService(Intent(context, OverlayService::class.java))
                    status = "Floating button on"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show floating button")
        }

        OutlinedButton(
            onClick = {
                context.stopService(Intent(context, OverlayService::class.java))
                status = "Floating button off"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hide floating button")
        }

        Text(status, fontSize = 18.sp, color = Color.Black)
        if (result.isNotBlank()) {
            Text(result, fontSize = 22.sp, color = Color.Black)
        }

        OutlinedButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showSettings) "Hide settings" else "Settings")
        }

        if (showSettings) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Groq API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Groq model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature (0 to 2)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    save()
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save settings")
            }
        }
    }
}

suspend fun generateBoost(
    apiKey: String,
    post: String,
    prompt: String,
    model: String,
    temperature: String
): String? = withContext(Dispatchers.IO) {
    try {
        val temp = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.7
        val userText = if (post.isBlank()) {
            "Write the sentence now. Do not explain."
        } else {
            "React to this specifically: \"$post\". Write ONE original sentence. Do not copy it."
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", prompt))
            .put(JSONObject().put("role", "user").put("content", userText))

        val body = JSONObject()
            .put("model", model)
            .put("temperature", temp)
            .put("max_completion_tokens", 40)
            .put("reasoning_effort", "none")
            .put("messages", messages)

        val conn = URL("https://api.groq.com/openai/v1/chat/completions")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader()
            .readText()
        conn.disconnect()

        val raw = JSONObject(text)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")

        raw.replace(Regex("(?is)<think>.*?</think>"), "")
            .replace(Regex("(?is)</?think>"), "")
            .replace(Regex("(?is)<reasoning>.*?</reasoning>"), "")
            .replace(Regex("(?is)here's a thinking process:.*"), "")
            .trim()
            .trim('"')
            .ifBlank { null }
    } catch (_: Exception) {
        null
    }
}