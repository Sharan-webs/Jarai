package com.kitagawh.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Groq {
    // Change these two lines if Groq renames a model.
    const val CHAT_MODEL = "llama-3.3-70b-versatile"
    const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    fun msg(role: String, content: Any): JSONObject = JSONObject().put("role", role).put("content", content)

    fun chat(key: String, model: String, messages: JSONArray, json: Boolean): String {
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.2)
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        val con = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 15000
        con.readTimeout = 45000
        con.doOutput = true
        con.setRequestProperty("Authorization", "Bearer $key")
        con.setRequestProperty("Content-Type", "application/json")
        try {
            con.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = con.responseCode
            val txt = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) throw IOException(friendly(code, txt))
            return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } finally {
            con.disconnect()
        }
    }

    private fun friendly(code: Int, txt: String): String = when (code) {
        401 -> "Groq rejected the API key. Check it in Settings, API."
        413 -> "That request was too large for Groq."
        429 -> "Groq rate limit reached. Wait a minute and try again."
        in 500..599 -> "Groq is having trouble right now. Try again shortly."
        else -> "Groq error $code: " + (try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { txt }).take(120)
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
                Groq.chat(key, Groq.CHAT_MODEL, JSONArray().put(Groq.msg("system", SYSTEM)).put(Groq.msg("user", prompt)), true)
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
        return Groq.chat(key, Groq.VISION_MODEL, JSONArray().put(Groq.msg("user", content)), false).trim()
    }
}
