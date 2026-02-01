package com.coffeecat.keyboard.providers

interface LanguageProvider {
    suspend fun getSuggestions(input: String): List<String>
    fun getComposingText(raw: String): String = raw
}