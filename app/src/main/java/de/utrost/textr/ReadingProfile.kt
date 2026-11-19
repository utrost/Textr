package de.utrost.textr

import android.content.SharedPreferences
import org.json.JSONObject

/** Metadata only. Null on ReadingState means follow the current global defaults. */
enum class ReadingFont(val css: String) { SANS("sans-serif"), SERIF("serif"), MONOSPACE("monospace") }

data class ReadingProfile(val fontSize: Int = 16, val wrap: Boolean = true,
    val font: ReadingFont = ReadingFont.SANS, val lineSpacing: Int = 160) {
    init { require(fontSize in 12..28); require(lineSpacing in 120..220) }
    fun json(): JSONObject = JSONObject().put("fontSize", fontSize).put("wrap", wrap)
        .put("font", font.name).put("lineSpacing", lineSpacing)
    fun save(preferences: SharedPreferences) = preferences.edit().putInt("fontSize", fontSize)
        .putBoolean("wrap", wrap).putString("font", font.name).putInt("lineSpacing", lineSpacing).apply()
    companion object {
        fun defaults(preferences: SharedPreferences) = ReadingProfile(
            preferences.getInt("fontSize", 16).coerceIn(12, 28), preferences.getBoolean("wrap", true),
            ReadingFont.entries.find { it.name == preferences.getString("font", null) } ?: ReadingFont.SANS,
            preferences.getInt("lineSpacing", 160).coerceIn(120, 220))
        fun fromJson(value: JSONObject?) = value?.let { ReadingProfile(
            it.optInt("fontSize", 16).coerceIn(12, 28), it.optBoolean("wrap", true),
            ReadingFont.entries.find { font -> font.name == it.optString("font") } ?: ReadingFont.SANS,
            it.optInt("lineSpacing", 160).coerceIn(120, 220)) }
    }
}
