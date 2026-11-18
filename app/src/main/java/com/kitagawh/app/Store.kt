package com.kitagawh.app

import android.content.Context

data class ApiSpec(val id: String, val name: String, val hint: String, val guide: List<String>)

object Api {
    val list = listOf(
        ApiSpec(
            id = "groq",
            name = "Groq",
            hint = "gsk_...",
            guide = listOf(
                "Open console.groq.com in your browser.",
                "Sign in with Google or email. It is free and needs no card.",
                "Open API Keys from the left menu.",
                "Tap Create API Key, give it any name and submit.",
                "Copy the key right away. Groq shows it only once.",
                "Paste it here and tap Save."
            )
        )
    )
}

object Store {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("kitagawh", Context.MODE_PRIVATE)

    fun str(c: Context, k: String, d: String = ""): String = sp(c).getString(k, d) ?: d
    fun putStr(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean = false): Boolean = sp(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int = 0): Int = sp(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()

    fun apiKey(c: Context, id: String) = str(c, "api_$id")
    fun putApiKey(c: Context, id: String, v: String) = putStr(c, "api_$id", v.trim())
    fun clearApiKeys(c: Context) {
        val e = sp(c).edit()
        Api.list.forEach { e.remove("api_" + it.id) }
        e.apply()
    }

    // Easy Touch slots: 10 entries, "-" means empty
    fun slots(c: Context): List<String?> {
        val raw = str(c, "et_slots", "")
        val parts = if (raw.isBlank()) emptyList() else raw.split(",")
        return List(10) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() && it != "-" } }
    }
    fun putSlots(c: Context, s: List<String?>) = putStr(c, "et_slots", s.joinToString(",") { it ?: "-" })

    fun etSize(c: Context) = int(c, "et_size", 56)
    fun etColor(c: Context) = int(c, "et_color", 0xFF7C8CFF.toInt())
    fun etAnim(c: Context) = int(c, "et_anim", 0)
}
