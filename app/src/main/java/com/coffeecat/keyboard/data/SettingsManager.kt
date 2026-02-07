package com.coffeecat.keyboard.data

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
    var backgroundImagePath: String?
        get() = prefs.getString("bg_image_path", null)
        set(value) = prefs.edit().putString("bg_image_path", value).apply()

    // --- 新增：圖片不透明度 (0.0 ~ 1.0) ---
    var backgroundImageAlpha: Float
        get() = prefs.getFloat("bg_image_alpha", 0.5f) // 預設 0.5，避免太亮
        set(value) = prefs.edit().putFloat("bg_image_alpha", value).apply()
    var backgroundColor: Int
        get() = prefs.getInt("bg_color", Color.parseColor("#49454F")) // 預設深色
        set(value) = prefs.edit().putInt("bg_color", value).apply()

    var accentColor: Int
        get() = prefs.getInt("accent_color", Color.parseColor("#5555aa"))
        set(value) = prefs.edit().putInt("accent_color", value).apply()

    var keyColor: Int
        get() = prefs.getInt("key_color", Color.parseColor("#888888"))
        set(value) = prefs.edit().putInt("key_color", value).apply()

    var toolbarColor: Int
        get() = prefs.getInt("toolbar_color", Color.parseColor("#20000000"))
        set(value) = prefs.edit().putInt("toolbar_color", value).apply()
    var functionKeyColor: Int
        get() = prefs.getInt("function_key_color", Color.parseColor("#aa33aa"))
        set(value) = prefs.edit().putInt("function_key_color", value).apply()
    var functionTextColor: Int
        get() = prefs.getInt("function_text_color", Color.parseColor("#ffaaaa"))
        set(value) = prefs.edit().putInt("function_text_color", value).apply()

    var textColor: Int
        get() = prefs.getInt("text_color", Color.WHITE)
        set(value) = prefs.edit().putInt("text_color", value).apply()

    var showOutline: Boolean
        get() = prefs.getBoolean("show_outline", true)
        set(value) = prefs.edit().putBoolean("show_outline", value).apply()
    var showButton: Boolean
        get() = prefs.getBoolean("show_button", true)
        set(value) = prefs.edit().putBoolean("show_button", value).apply()
    var showText: Boolean
        get() = prefs.getBoolean("show_text", true)
        set(value) = prefs.edit().putBoolean("show_text", value).apply()

    var fontFamily: Int
        get() = prefs.getInt("font_family", 0) // 0: Sans, 1: Serif, 2: Monospace
        set(value) = prefs.edit().putInt("font_family", value).apply()
    fun getTypeface(): Typeface = when (fontFamily) {
        1 -> Typeface.SERIF
        2 -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
    }
    private val PREFS_NAME = "emoji_prefs"
    private val KEY_RECENT_EMOJIS = "recent_emojis"
    private val MAX_RECENT_COUNT = 24 // 最多存幾個

    fun getRecentEmojis(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedString = prefs.getString(KEY_RECENT_EMOJIS, "") ?: ""
        if (savedString.isEmpty()) return emptyList()
        return savedString.split(",")
    }

    fun addRecentEmoji(context: Context, emoji: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getRecentEmojis(context).toMutableList()

        // 1. 如果已經存在，先移除（為了把它搬到最前面）
        currentList.remove(emoji)

        // 2. 加到最前面
        currentList.add(0, emoji)

        // 3. 超過上限就移除最後一個
        if (currentList.size > MAX_RECENT_COUNT) {
            currentList.removeAt(currentList.size - 1)
        }

        // 4. 存回字串 (用逗號隔開)
        prefs.edit().putString(KEY_RECENT_EMOJIS, currentList.joinToString(",")).apply()
    }
}