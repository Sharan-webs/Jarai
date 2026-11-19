package com.kitagawh.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Groq {
    // Groq changes its model list often, so the app asks Groq which models this key can use
    // and picks the first one it finds from these lists (best first).
    private val CHAT_PREF = listOf(
        "openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b", "qwen/qwen3.6-27b",
        "llama-3.3-70b-versatile", "llama-3.1-8b-instant"
    )
    private val VISION_PREF = listOf(
        "qwen/qwen3.8-27b", "qwen/qwen3.6-27b", "meta-models/muse-spark-open",
        "meta-llama/llama-4-scout-17b-16e-instruct"
    )
    private val SKIP = listOf("whisper", "guard", "safeguard", "tts", "orpheus", "compound", "embed", "prompt")
    private const val BASE = "https://api.groq.com/openai/v1"

    @Volatile private var cached: List<String> = emptyList()
    @Volatile private var cachedAt = 0L

    fun msg(role: String, content: Any): JSONObject = JSONObject().put("role", role).put("content", content)

    private fun http(key: String, method: String, url: String, body: String?): Pair<Int, String> {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 15000
        con.readTimeout = 60000
        con.setRequestProperty("Authorization", "Bearer $key")
        con.setRequestProperty("Content-Type", "application/json")
        try {
            if (body != null) {
                con.doOutput = true
                con.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = con.responseCode
            val txt = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
            return code to txt
        } finally {
            con.disconnect()
        }
    }

    fun available(key: String): List<String> {
        if (cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < 600000) return cached
        val (code, txt) = http(key, "GET", "$BASE/models", null)
        if (code !in 200..299) throw IOException(friendly(code, txt))
        val arr = JSONObject(txt).getJSONArray("data")
        val ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        cached = ids
        cachedAt = System.currentTimeMillis()
        return ids
    }

    private fun candidates(key: String, vision: Boolean): List<String> {
        val ids = available(key)
        val picked = (if (vision) VISION_PREF else CHAT_PREF).filter { it in ids }
        if (vision) return picked
        val rest = ids.filter { id -> id !in picked && SKIP.none { id.contains(it) } }
        return picked + rest.take(3)
    }

    fun chat(key: String, messages: JSONArray, json: Boolean, vision: Boolean = false): String {
        val models = candidates(key, vision)
        if (models.isEmpty()) {
            throw IOException(if (vision) "Your Groq key has no image model right now, so Screen search cannot run." else "Your Groq key has no chat model available.")
        }
        var last = "Groq has no usable model for this key."
        for (m in models) {
            val body = JSONObject().put("model", m).put("messages", messages).put("temperature", 0.2)
            if (json) body.put("response_format", JSONObject().put("type", "json_object"))
            if (m.startsWith("openai/gpt-oss")) body.put("reasoning_effort", "low")
            val (code, txt) = http(key, "POST", "$BASE/chat/completions", body.toString())
            if (code in 200..299) {
                val c = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
                return c.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            }
            last = friendly(code, txt)
            if (code == 404 || code == 400 || code == 403) continue // model gone or not allowed: try the next one
            throw IOException(last)
        }
        throw IOException(last)
    }

    private fun friendly(code: Int, txt: String): String = when (code) {
        401 -> "Groq rejected the API key. Check it in Settings, API."
        413 -> "That request was too large for Groq."
        429 -> "Groq rate limit reached. Wait a minute and try again."
        in 500..599 -> "Groq is having trouble right now. Try again shortly."
        else -> "Groq error $code: " + (try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { txt }).take(140)
    }
}

object Agent {
    private const val SYSTEM = """You are Jarvis, the brain of an Android phone assistant called Kitagawh. You control the phone one step at a time.
Reply with ONLY a JSON object: {"say":"max 8 words","action":"<name>","args":{...}}

Actions:
open_app {name}  - full app name, e.g. "YouTube"
open_settings {page} - wifi, hotspot, bluetooth, display, sound, battery, apps, airplane, location, nfc, storage, date, accessibility, mobile_data, main
open_url {url}
click {index} - index number from the screen list
click_text {text}
type {text}
submit - press enter in the focused text field
scroll {dir: up|down}
back, home, recents, notifications, quick_settings, lock, screenshot
torch {on: true|false}
volume {dir: up|down|mute} or {percent: 0-100}
brightness {percent}
wait {ms}
done - the task is finished or impossible; put the final spoken answer in "say"

Rules:
- Do the smallest number of steps. Torch, volume, brightness, open app and simple settings are ONE action, then done.
- Each screen line is: index: text [description] flags. Prefer click with the index.
- Before toggling a switch, read its state (CHECKED or unchecked) so you never flip it the wrong way. If it already matches the request, say done.
- For hotspot: open_settings hotspot, then click the portable hotspot row or switch. For forgetting a Wi-Fi network: open_settings wifi, click the network, then click Forget.
- After the last action, if the screen shows the goal reached, reply done.
- Never use banking, payment or password screens unless the user clearly asked.
- If the request is a question and not a phone task, answer it in "say" and use done."""

    fun run(
        c: Context,
        task: String,
        say: (String) -> Unit,
        speak: (String) -> Unit,
        cancelled: () -> Boolean
    ): String? {
        val key = Store.apiKey(c, "groq")
        if (key.isBlank()) return "Add your Groq API key in Settings, API."
        val acc = KitaAccessibilityService.instance ?: return "Accessibility is off. Turn it on in Kitagawh permissions."
        val history = StringBuilder()
        for (step in 1..14) {
            if (cancelled()) return null
            val screen = acc.dumpScreen()
            val prompt = "TASK: $task\n\nACTIONS SO FAR:\n${if (history.isEmpty()) "(none)" else history}\nCURRENT SCREEN:\n$screen\n\nReply with the next JSON action."
            val reply = try {
                Groq.chat(key, JSONArray().put(Groq.msg("system", SYSTEM)).put(Groq.msg("user", prompt)), true)
            } catch (e: Exception) {
                return e.message ?: "Could not reach Groq. Check your internet."
            }
            if (cancelled()) return null
            val j = try { JSONObject(reply) } catch (e: Exception) { return "The AI sent an unreadable answer. Try again." }
            val action = j.optString("action")
            val args = j.optJSONObject("args") ?: JSONObject()
            val s = j.optString("say")
            if (s.isNotBlank()) say(s)
            if (action == "done" || action.isBlank()) {
                if (s.isNotBlank()) speak(s)
                return null
            }
            val res = Actions.exec(c, acc, action, args)
            history.append(step).append(". ").append(action).append(' ').append(args).append(" -> ").append(res).append('\n')
            Thread.sleep(if (action == "open_app" || action == "open_settings" || action == "open_url") 1400 else 800)
        }
        return "I could not finish that in time."
    }

    fun describeScreen(c: Context, b64: String): String {
        val key = Store.apiKey(c, "groq")
        if (key.isBlank()) throw IOException("Add your Groq API key in Settings, API.")
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text",
                "This is a phone screenshot. In at most 5 short plain sentences, say what is on the screen and what the main thing is (an app, a person, a product, a place, an error). If it is something people look up, say what it is and one useful fact."))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
        return Groq.chat(key, JSONArray().put(Groq.msg("user", content)), false, vision = true).trim()
    }
}
