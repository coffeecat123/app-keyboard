package com.coffeecat.keyboard.providers

import com.coffeecat.keyboard.engine.BopomofoEngine

class BopomofoProvider(private val engine: BopomofoEngine) : LanguageProvider {
    override suspend fun getSuggestions(input: String): List<String> {
        // 直接封裝 BopomofoEngine 的邏輯
        return engine.getSuggestions(input)
    }

    override fun getComposingText(raw: String): String {
        // 未來可以在此處理注音符號的組合顯示邏輯
        return raw
    }
}