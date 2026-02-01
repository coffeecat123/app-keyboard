package com.coffeecat.keyboard.providers

import com.coffeecat.keyboard.engine.EnglishEngine

class EnglishProvider(private val engine: EnglishEngine) : LanguageProvider {
    override suspend fun getSuggestions(input: String): List<String> {
        val raw = input.lowercase()
        val exact = engine.getSuggestions(raw)

        // 將原本寫在 IME 的邏輯搬過來
        val fuzzy = if (raw.length >= 3) {
            engine.getFuzzySuggestions(raw, 1)
        } else {
            emptyList()
        }

        // 加上原始輸入，並去重
        return (listOf(raw) + exact + fuzzy).distinct()
    }
}