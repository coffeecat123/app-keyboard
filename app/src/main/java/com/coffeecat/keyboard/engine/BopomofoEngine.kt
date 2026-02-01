package com.coffeecat.keyboard.engine

import android.util.Log

class BopomofoEngine {
    private val TAG = "BopomofoEngine"
    private val TONE_MAP = mapOf(' ' to '1', 'ˊ' to '2', 'ˇ' to '3', 'ˋ' to '4', '˙' to '0')

    // 精確注音組合清單
    private val validSyllables = setOf(
        "ㄓ","ㄔ","ㄕ","ㄖ","ㄗ","ㄘ","ㄙ","ㄚ","ㄅㄚ","ㄆㄚ","ㄇㄚ","ㄈㄚ","ㄉㄚ","ㄊㄚ","ㄋㄚ","ㄌㄚ","ㄍㄚ","ㄎㄚ","ㄏㄚ","ㄓㄚ","ㄔㄚ","ㄕㄚ","ㄗㄚ","ㄘㄚ","ㄙㄚ","ㄛ","ㄅㄛ","ㄆㄛ","ㄇㄛ","ㄈㄛ","ㄜ","ㄇㄜ","ㄉㄜ","ㄊㄜ","ㄋㄜ","ㄌㄜ","ㄍㄜ","ㄎㄜ","ㄏㄜ","ㄓㄜ","ㄔㄜ","ㄕㄜ","ㄖㄜ","ㄗㄜ","ㄘㄜ","ㄙㄜ","ㄝ","ㄞ","ㄅㄞ","ㄆㄞ","ㄇㄞ","ㄉㄞ","ㄊㄞ","ㄋㄞ","ㄌㄞ","ㄍㄞ","ㄎㄞ","ㄏㄞ","ㄓㄞ","ㄔㄞ","ㄕㄞ","ㄗㄞ","ㄘㄞ","ㄙㄞ","ㄟ","ㄅㄟ","ㄆㄟ","ㄇㄟ","ㄈㄟ","ㄉㄟ","ㄋㄟ","ㄌㄟ","ㄍㄟ","ㄏㄟ","ㄓㄟ","ㄕㄟ","ㄗㄟ","ㄠ","ㄅㄠ","ㄆㄠ","ㄇㄠ","ㄉㄠ","ㄊㄠ","ㄋㄠ","ㄌㄠ","ㄍㄠ","ㄎㄠ","ㄏㄠ","ㄓㄠ","ㄔㄠ","ㄕㄠ","ㄖㄠ","ㄗㄠ","ㄘㄠ","ㄙㄠ","ㄡ","ㄆㄡ","ㄇㄡ","ㄈㄡ","ㄉㄡ","ㄊㄡ","ㄋㄡ","ㄌㄡ","ㄍㄡ","ㄎㄡ","ㄏㄡ","ㄓㄡ","ㄔㄡ","ㄕㄡ","ㄖㄡ","ㄗㄡ","ㄘㄡ","ㄙㄡ","ㄢ","ㄅㄢ","ㄆㄢ","ㄇㄢ","ㄈㄢ","ㄉㄢ","ㄊㄢ","ㄋㄢ","ㄌㄢ","ㄍㄢ","ㄎㄢ","ㄏㄢ","ㄓㄢ","ㄔㄢ","ㄕㄢ","ㄖㄢ","ㄗㄢ","ㄘㄢ","ㄙㄢ","ㄣ","ㄅㄣ","ㄆㄣ","ㄇㄣ","ㄈㄣ","ㄋㄣ","ㄍㄣ","ㄎㄣ","ㄏㄣ","ㄓㄣ","ㄔㄣ","ㄕㄣ","ㄖㄣ","ㄗㄣ","ㄘㄣ","ㄙㄣ","ㄤ","ㄅㄤ","ㄆㄤ","ㄇㄤ","ㄈㄤ","ㄉㄤ","ㄊㄤ","ㄋㄤ","ㄌㄤ","ㄍㄤ","ㄎㄤ","ㄏㄤ","ㄓㄤ","ㄔㄤ","ㄕㄤ","ㄖㄤ","ㄗㄤ","ㄘㄤ","ㄙㄤ","ㄥ","ㄅㄥ","ㄆㄥ","ㄇㄥ","ㄈㄥ","ㄉㄥ","ㄊㄥ","ㄋㄥ","ㄌㄥ","ㄍㄥ","ㄎㄥ","ㄏㄥ","ㄓㄥ","ㄔㄥ","ㄕㄥ","ㄖㄥ","ㄗㄥ","ㄘㄥ","ㄙㄥ","ㄦ","ㄧ","ㄅㄧ","ㄆㄧ","ㄇㄧ","ㄉㄧ","ㄊㄧ","ㄋㄧ","ㄌㄧ","ㄐㄧ","ㄑㄧ","ㄒㄧ","ㄧㄚ","ㄌㄧㄚ","ㄐㄧㄚ","ㄑㄧㄚ","ㄒㄧㄚ","ㄧㄛ","ㄧㄝ","ㄅㄧㄝ","ㄆㄧㄝ","ㄇㄧㄝ","ㄉㄧㄝ","ㄊㄧㄝ","ㄋㄧㄝ","ㄌㄧㄝ","ㄐㄧㄝ","ㄑㄧㄝ","ㄒㄧㄝ","ㄧㄞ","ㄧㄠ","ㄅㄧㄠ","ㄆㄧㄠ","ㄇㄧㄠ","ㄉㄧㄠ","ㄊㄧㄠ","ㄋㄧㄠ","ㄌㄧㄠ","ㄐㄧㄠ","ㄑㄧㄠ","ㄒㄧㄠ","ㄧㄡ","ㄇㄧㄡ","ㄉㄧㄡ","ㄋㄧㄡ","ㄌㄧㄡ","ㄐㄧㄡ","ㄑㄧㄡ","ㄒㄧㄡ","ㄧㄢ","ㄅㄧㄢ","ㄆㄧㄢ","ㄇㄧㄢ","ㄉㄧㄢ","ㄊㄧㄢ","ㄋㄧㄢ","ㄌㄧㄢ","ㄐㄧㄢ","ㄑㄧㄢ","ㄒㄧㄢ","ㄧㄣ","ㄅㄧㄣ","ㄆㄧㄣ","ㄇㄧㄣ","ㄋㄧㄣ","ㄌㄧㄣ","ㄐㄧㄣ","ㄑㄧㄣ","ㄒㄧㄣ","ㄧㄤ","ㄋㄧㄤ","ㄌㄧㄤ","ㄐㄧㄤ","ㄑㄧㄤ","ㄒㄧㄤ","ㄧㄥ","ㄅㄧㄥ","ㄆㄧㄥ","ㄇㄧㄥ","ㄉㄧㄥ","ㄊㄧㄥ","ㄋㄧㄥ","ㄌㄧㄥ","ㄐㄧㄥ","ㄑㄧㄥ","ㄒㄧㄥ","ㄨ","ㄅㄨ","ㄆㄨ","ㄇㄨ","ㄈㄨ","ㄉㄨ","ㄊㄨ","ㄋㄨ","ㄌㄨ","ㄍㄨ","ㄎㄨ","ㄏㄨ","ㄓㄨ","ㄔㄨ","ㄕㄨ","ㄖㄨ","ㄗㄨ","ㄘㄨ","ㄙㄨ","ㄨㄚ","ㄍㄨㄚ","ㄎㄨㄚ","ㄏㄨㄚ","ㄓㄨㄚ","ㄔㄨㄚ","ㄕㄨㄚ","ㄨㄛ","ㄉㄨㄛ","ㄊㄨㄛ","ㄋㄨㄛ","ㄌㄨㄛ","ㄍㄨㄛ","ㄎㄨㄛ","ㄏㄨㄛ","ㄓㄨㄛ","ㄔㄨㄛ","ㄕㄨㄛ","ㄖㄨㄛ","ㄗㄨㄛ","ㄘㄨㄛ","ㄙㄨㄛ","ㄨㄞ","ㄍㄨㄞ","ㄎㄨㄞ","ㄏㄨㄞ","ㄓㄨㄞ","ㄔㄨㄞ","ㄕㄨㄞ","ㄨㄟ","ㄉㄨㄟ","ㄊㄨㄟ","ㄍㄨㄟ","ㄎㄨㄟ","ㄏㄨㄟ","ㄓㄨㄟ","ㄔㄨㄟ","ㄕㄨㄟ","ㄖㄨㄟ","ㄗㄨㄟ","ㄘㄨㄟ","ㄙㄨㄟ","ㄨㄢ","ㄉㄨㄢ","ㄊㄨㄢ","ㄋㄨㄢ","ㄌㄨㄢ","ㄍㄨㄢ","ㄎㄨㄢ","ㄏㄨㄢ","ㄓㄨㄢ","ㄔㄨㄢ","ㄕㄨㄢ","ㄖㄨㄢ","ㄗㄨㄢ","ㄘㄨㄢ","ㄙㄨㄢ","ㄨㄣ","ㄉㄨㄣ","ㄊㄨㄣ","ㄌㄨㄣ","ㄍㄨㄣ","ㄎㄨㄣ","ㄏㄨㄣ","ㄓㄨㄣ","ㄔㄨㄣ","ㄕㄨㄣ","ㄖㄨㄣ","ㄗㄨㄣ","ㄘㄨㄣ","ㄙㄨㄣ","ㄨㄤ","ㄍㄨㄤ","ㄎㄨㄤ","ㄏㄨㄤ","ㄓㄨㄤ","ㄔㄨㄤ","ㄕㄨㄤ","ㄨㄥ","ㄉㄨㄥ","ㄊㄨㄥ","ㄋㄨㄥ","ㄌㄨㄥ","ㄍㄨㄥ","ㄎㄨㄥ","ㄏㄨㄥ","ㄓㄨㄥ","ㄔㄨㄥ","ㄖㄨㄥ","ㄗㄨㄥ","ㄘㄨㄥ","ㄙㄨㄥ","ㄩ","ㄋㄩ","ㄌㄩ","ㄐㄩ","ㄑㄩ","ㄒㄩ","ㄩㄝ","ㄋㄩㄝ","ㄌㄩㄝ","ㄐㄩㄝ","ㄑㄩㄝ","ㄒㄩㄝ","ㄩㄢ","ㄐㄩㄢ","ㄑㄩㄢ","ㄒㄩㄢ","ㄩㄣ","ㄐㄩㄣ","ㄑㄩㄣ","ㄒㄩㄣ","ㄩㄥ","ㄐㄩㄥ","ㄑㄩㄥ","ㄒㄩㄥ"
    ).map { it.replace('一', 'ㄧ') }.toSet()

    data class Candidate(
        val word: String,
        val weight: Double,
        val syllableKeys: List<String>,
        val initials: List<String>
    )

    class TrieNode {
        var children: MutableMap<Char, TrieNode>? = null
        var candidates: MutableList<Candidate>? = null
        fun getOrCreateChild(char: Char): TrieNode = (children ?: mutableMapOf<Char, TrieNode>().also { children = it }).getOrPut(char) { TrieNode() }
    }

    private val root = TrieNode()
    private val lock = Any()

    fun insertDetailed(word: String, key: String, tones: String, initialsStr: String, length: Int, weight: Double) {
        val keys = key.split(",").map { it.trim().replace(" ", "") }.filter { it.isNotEmpty() }
        val toneList = tones.split(",").map { it.trim().ifEmpty { "1" } }
        val inits = initialsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val actualCount = keys.size
        if (actualCount == 0) return

        val syllableKeys = keys.mapIndexed { i, s -> s + toneList.getOrElse(i) { "1" } }
        val finalInits = if (inits.size >= actualCount) inits.take(actualCount) else keys.map { it.take(1) }

        val cand = Candidate(word, weight, syllableKeys, finalInits)

        synchronized(lock) {
            var curr = root
            val fullInitPath = finalInits.joinToString("")
            for (char in fullInitPath) curr = curr.getOrCreateChild(char)
            if (curr.candidates == null) curr.candidates = mutableListOf()
            curr.candidates!!.add(cand)
        }
    }

    fun getSuggestions(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        var normalized = input.replace('一', 'ㄧ')
        TONE_MAP.forEach { (char, num) -> normalized = normalized.replace(char.toString(), num.toString()) }

        val inputSegments = splitPhoneticUnits(normalized)
        if (inputSegments.isEmpty()) return emptyList()

        // 提取每個音節的首字母
        val inputInitials = inputSegments.map { it.filter { c -> c !in "01234" }.take(1) }
            .filter { it.isNotEmpty() }
            .joinToString("")

        val results = mutableSetOf<Candidate>()
        synchronized(lock) { searchByInitials(root, inputInitials, 0, results) }

        return results.filter { cand ->
            // 字數過濾：輸入幾個音節段，就找幾個字的詞
            if (cand.syllableKeys.size != inputSegments.size) return@filter false

            for (i in inputSegments.indices) {
                val seg = inputSegments[i]
                val candSyl = cand.syllableKeys[i]

                val inputHasTone = seg.any { it in "01234" }
                val pureSeg = seg.filter { it !in "01234" }
                val pureCand = candSyl.filter { it !in "01234" }

                if (inputHasTone) {
                    // 有聲調：必須完全一致 (ㄌㄧㄠ3 == ㄌㄧㄠ3)
                    if (seg != candSyl) return@filter false
                } else {
                    // 沒有聲調：採取前綴匹配 (Prefix Match)
                    // 核心修改：這能讓「ㄐㄧ」匹配到「ㄐㄧㄝ」、「ㄐㄧㄚ」等
                    if (!pureCand.startsWith(pureSeg)) return@filter false
                }
            }
            true
        }.sortedByDescending { it.weight }.map { it.word }.distinct().take(30)
    }

    private fun splitPhoneticUnits(input: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        // 按聲調強制切分
        for (i in input.indices) {
            if (input[i] in "01234") {
                val chunk = input.substring(start, i + 1)
                result.addAll(subSplitSyllables(chunk))
                start = i + 1
            }
        }
        if (start < input.length) {
            result.addAll(subSplitSyllables(input.substring(start)))
        }
        return result
    }

    private fun subSplitSyllables(chunk: String): List<String> {
        val subResult = mutableListOf<String>()
        val tone = chunk.find { it in "01234" }
        val content = chunk.filter { it !in "01234" }
        if (content.isEmpty()) return emptyList()

        var i = 0
        while (i < content.length) {
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len <= content.length) {
                    val sub = content.substring(i, i + len)
                    if (validSyllables.contains(sub)) {
                        subResult.add(sub)
                        i += len
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                subResult.add(content[i].toString())
                i++
            }
        }

        if (tone != null && subResult.isNotEmpty()) {
            val lastIdx = subResult.size - 1
            subResult[lastIdx] = subResult[lastIdx] + tone
        }
        return subResult
    }

    private fun searchByInitials(node: TrieNode, initials: String, index: Int, results: MutableSet<Candidate>) {
        if (index >= initials.length) {
            collectAll(node, results)
            return
        }
        node.children?.get(initials[index])?.let { searchByInitials(it, initials, index + 1, results) }
    }

    private fun collectAll(node: TrieNode, results: MutableSet<Candidate>) {
        node.candidates?.let { results.addAll(it) }
        node.children?.values?.forEach { collectAll(it, results) }
    }
}