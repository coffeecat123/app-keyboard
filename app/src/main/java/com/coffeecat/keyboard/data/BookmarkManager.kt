package com.coffeecat.keyboard.data

import android.content.Context
import org.json.JSONArray
import androidx.core.content.edit

class BookmarkManager(context: Context) {

    companion object {
        private const val PREF_NAME = "bookmarks_prefs"
        private const val KEY_BOOKMARKS = "bookmarks_json"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // --- 緩存機制 ---
    // 使用 lazy 確保只在第一次需要時讀取磁碟
    private var cachedBookmarks: MutableList<String>? = null

    private fun getCachedData(): MutableList<String> {
        if (cachedBookmarks == null) {
            val json = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
            val array = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            cachedBookmarks = list
        }
        return cachedBookmarks!!
    }

    fun getBookmarks(): List<String> {
        return getCachedData().toList() // 傳回副本以防外部修改
    }

    fun addBookmark(text: String) {
        val current = getCachedData()

        // 如果已存在則移除，達成「移動到最前面」的效果
        current.remove(text)
        current.add(0, text)

        saveBookmarks(current)
    }

    fun removeBookmark(text: String) {
        val current = getCachedData()
        if (current.remove(text)) {
            saveBookmarks(current)
        }
    }

    private fun saveBookmarks(list: List<String>) {
        // 更新快取的同時非同步寫入磁碟
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit { putString(KEY_BOOKMARKS, array.toString()) }
    }
    fun isBookmarked(text: String): Boolean {
        return getBookmarks().contains(text)
    }
}