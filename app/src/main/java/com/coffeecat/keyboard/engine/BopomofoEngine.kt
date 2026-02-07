package com.coffeecat.keyboard.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.coffeecat.keyboard.data.DictionaryHelper

class BopomofoEngine(context: Context) {
    private val db: SQLiteDatabase = DictionaryHelper(context).openDatabase()
    private val TONE_MAP = mapOf(' ' to '1', 'ˊ' to '2', 'ˇ' to '3', 'ˋ' to '4', '˙' to '0')

    private val PROXIMITY_PENALTY = -20.0

    // 鄰近按鍵表 (保持不變)
    private val proximityMap = mapOf(
        'ㄅ' to "ㄉㄆ", 'ㄆ' to "ㄅㄉㄊㄇ", 'ㄇ' to "ㄆㄋㄈㄌ", 'ㄈ' to "ㄇㄌ",
        'ㄉ' to "ㄅㄆㄊ", 'ㄊ' to "ㄉㄆㄍㄋ", 'ㄋ' to "ㄊㄇㄎㄌㄏ", 'ㄌ' to "ㄈㄇㄋㄏ",
        'ㄍ' to "ㄊㄐㄎ", 'ㄎ' to "ㄍㄋㄑㄏㄒ", 'ㄏ' to "ㄌㄋㄎㄒ", 'ㄐ' to "ㄍㄑㄔ",
        'ㄑ' to "ㄐㄎㄕㄖ", 'ㄒ' to "ㄏㄎㄑㄖ", 'ㄓ' to "ㄐㄔ", 'ㄔ' to "ㄓㄐㄕㄗ",
        'ㄕ' to "ㄔㄑㄘㄙ", 'ㄖ' to "ㄒㄑㄕㄙ", 'ㄗ' to "ㄔㄘ", 'ㄘ' to "ㄗㄕ",
        'ㄙ' to "ㄕㄖ", 'ㄧ' to "ㄨ", 'ㄨ' to "一ㄩ", 'ㄩ' to "ㄨ",
        'ㄚ' to "ㄞㄛ", 'ㄛ' to "ㄚㄟㄜ", 'ㄜ' to "ㄛㄠㄡ", 'ㄝ' to "ㄜㄡ",
        'ㄞ' to "ㄚㄛㄟ", 'ㄟ' to "ㄞㄛㄠ", 'ㄠ' to "ㄟㄜㄤㄥ", 'ㄡ' to "ㄝㄜㄥ",
        'ㄢ' to "ㄞㄟㄣ", 'ㄣ' to "ㄢㄟㄤ", 'ㄤ' to "ㄣㄠ", 'ㄥ' to "ㄠㄡ", 'ㄦ' to "ㄢㄣ"
    )

    data class ScoredSuggestion(val display: String, val wordLen: Int, val score: Double, val isNgram: Int)
    data class ViterbiNode(val word: String, val score: Double, val startIdx: Int, val endIdx: Int, var totalScore: Double = -1e18, var bestPrev: ViterbiNode? = null)
    data class DbMatch(val word: String, val weight: Double, val isNgram: Int, val isFuzzy: Boolean)

    private val validSyllables = setOf(
        "ㄓ","ㄔ","ㄕ","ㄖ","ㄗ","ㄘ","ㄙ","ㄚ","ㄅㄚ","ㄆㄚ","ㄇㄚ","ㄈㄚ","ㄉㄚ","ㄊㄚ","ㄋㄚ","ㄌㄚ","ㄍㄚ","ㄎㄚ","ㄏㄚ","ㄓㄚ","ㄔㄚ","ㄕㄚ","ㄗㄚ","ㄘㄚ","ㄙㄚ","ㄛ","ㄅㄛ","ㄆㄛ","ㄇㄛ","ㄈㄛ","ㄜ","ㄇㄜ","ㄉㄜ","ㄊㄜ","ㄋㄜ","ㄌㄜ","ㄍㄜ","ㄎㄜ","ㄏㄜ","ㄓㄜ","ㄔㄜ","ㄕㄜ","ㄖㄜ","ㄗㄜ","ㄘㄜ","ㄙㄜ","ㄝ","ㄞ","ㄅㄞ","ㄆㄞ","ㄇㄞ","ㄉㄞ","ㄊㄞ","ㄋㄞ","ㄌㄞ","ㄍㄞ","ㄎㄞ","ㄏㄞ","ㄓㄞ","ㄔㄞ","ㄕㄞ","ㄗㄞ","ㄘㄞ","ㄙㄞ","ㄟ","ㄅㄟ","ㄆㄟ","ㄇㄟ","ㄈㄟ","ㄉㄟ","ㄋㄟ","ㄌㄟ","ㄍㄟ","ㄏㄟ","ㄓㄟ","ㄕㄟ","ㄗㄟ","ㄠ","ㄅㄠ","ㄆㄠ","ㄇㄠ","ㄉㄠ","ㄊㄠ","ㄋㄠ","ㄌㄠ","ㄍㄠ","ㄎㄠ","ㄏㄠ","ㄓㄠ","ㄔㄠ","ㄕㄠ","ㄖㄠ","ㄗㄠ","ㄘㄠ","ㄙㄠ","ㄡ","ㄆㄡ","ㄇㄡ","ㄈㄡ","ㄉㄡ","ㄊㄡ","ㄋㄡ","ㄌㄡ","ㄍㄡ","ㄎㄡ","ㄏㄡ","ㄓㄡ","ㄔㄡ","ㄕㄡ","ㄖㄡ","ㄗㄡ","ㄘㄡ","ㄙㄡ","ㄢ","ㄅㄢ","ㄆㄢ","ㄇㄢ","ㄈㄢ","ㄉㄢ","ㄊㄢ","ㄋㄢ","ㄌㄢ","ㄍㄢ","ㄎㄢ","ㄏㄢ","ㄓㄢ","ㄔㄢ","ㄕㄢ","ㄖㄢ","ㄗㄢ","ㄘㄢ","ㄙㄢ","ㄣ","ㄅㄣ","ㄆㄣ","ㄇㄣ","ㄈㄣ","ㄋㄣ","ㄍㄣ","ㄎㄣ","ㄏㄣ","ㄓㄣ","ㄔㄣ","ㄕㄣ","ㄖㄣ","ㄗㄣ","ㄘㄣ","ㄙㄣ","ㄤ","ㄅㄤ","ㄆㄤ","ㄇㄤ","ㄈㄤ","ㄉㄤ","ㄊㄤ","ㄋㄤ","ㄌㄤ","ㄍㄤ","ㄎㄤ","ㄏㄤ","ㄓㄤ","ㄔㄤ","ㄕㄤ","ㄖㄤ","ㄗㄤ","ㄘㄤ","ㄙㄤ","ㄥ","ㄅㄥ","ㄆㄥ","ㄇㄥ","ㄈㄥ","ㄉㄥ","ㄊㄥ","ㄋㄥ","ㄌㄥ","ㄍㄥ","ㄎㄥ","ㄏㄥ","ㄓㄥ","ㄔㄥ","ㄕㄥ","ㄖㄥ","ㄗㄥ","ㄘㄥ","ㄙㄥ","ㄦ","ㄧ","ㄅㄧ","ㄆㄧ","ㄇㄧ","ㄉㄧ","ㄊㄧ","ㄋㄧ","ㄌㄧ","ㄐㄧ","ㄑㄧ","ㄒㄧ","ㄧㄚ","ㄌㄧㄚ","ㄐㄧㄚ","ㄑㄧㄚ","ㄒㄧㄚ","ㄧㄛ","ㄧㄝ","ㄅㄧㄝ","ㄆㄧㄝ","ㄇㄧㄝ","ㄉㄧㄝ","ㄊㄧㄝ","ㄋㄧㄝ","ㄌㄧㄝ","ㄐㄧㄝ","ㄑㄧㄝ","ㄒㄧㄝ","ㄧㄞ","ㄧㄠ","ㄅㄧㄠ","ㄆㄧㄠ","ㄇㄧㄠ","ㄉㄧㄠ","ㄊㄧㄠ","ㄋㄧㄠ","ㄌㄧㄠ","ㄐㄧㄠ","ㄑㄧㄠ","ㄒㄧㄠ","ㄧㄡ","ㄇㄧㄡ","ㄉㄧㄡ","ㄋㄧㄡ","ㄌㄧㄡ","ㄐㄧㄡ","ㄑㄧㄡ","ㄒㄧㄡ","ㄧㄢ","ㄅㄧㄢ","ㄆㄧㄢ","ㄇㄧㄢ","ㄉㄧㄢ","ㄊㄧㄢ","ㄋㄧㄢ","ㄌㄧㄢ","ㄐㄧㄢ","ㄑㄧㄢ","ㄒㄧㄢ","ㄧㄣ","ㄅㄧㄣ","ㄆㄧㄣ","ㄇㄧㄣ","ㄋㄧㄣ","ㄌㄧㄣ","ㄐㄧㄣ","ㄑㄧㄣ","ㄒㄧㄣ","ㄧㄤ","ㄋㄧㄤ","ㄌㄧㄤ","ㄐㄧㄤ","ㄑㄧㄤ","ㄒㄧㄤ","ㄧㄥ","ㄅㄧㄥ","ㄆㄧㄥ","ㄇㄧㄥ","ㄉㄧㄥ","ㄊㄧㄥ","ㄋㄧㄥ","ㄌㄧㄥ","ㄐㄧㄥ","ㄑㄧㄥ","ㄒㄧㄥ","ㄨ","ㄅㄨ","ㄆㄨ","ㄇㄨ","ㄈㄨ","ㄉㄨ","ㄊㄨ","ㄋㄨ","ㄌㄨ","ㄍㄨ","ㄎㄨ","ㄏㄨ","ㄓㄨ","ㄔㄨ","ㄕㄨ","ㄖㄨ","ㄗㄨ","ㄘㄨ","ㄙㄨ","ㄨㄚ","ㄍㄨㄚ","ㄎㄨㄚ","ㄏㄨㄚ","ㄓㄨㄚ","ㄔㄨㄚ","ㄕㄨㄚ","ㄨㄛ","ㄉㄨㄛ","ㄊㄨㄛ","ㄋㄨㄛ","ㄌㄨㄛ","ㄍㄨㄛ","ㄎㄨㄛ","ㄏㄨㄛ","ㄓㄨㄛ","ㄔㄨㄛ","ㄕㄨㄛ","ㄖㄨㄛ","ㄗㄨㄛ","ㄘㄨㄛ","ㄙㄨㄛ","ㄨㄞ","ㄍㄨㄞ","ㄎㄨㄞ","ㄏㄨㄞ","ㄓㄨㄞ","ㄔㄨㄚ","ㄕㄨㄞ","ㄨㄟ","ㄉㄨㄟ","ㄊㄨㄟ","ㄍㄨㄟ","ㄎㄨㄟ","ㄏㄨㄟ","ㄓㄨㄟ","ㄔㄨㄟ","ㄕㄨㄟ","ㄖㄨㄟ","ㄗㄨㄟ","ㄘㄨㄟ","ㄙㄨㄟ","ㄨㄢ","ㄉㄨㄢ","ㄊㄨㄢ","ㄋㄨㄢ","ㄌㄨㄢ","ㄍㄨㄢ","ㄎㄨㄢ","ㄏㄨㄢ","ㄓㄨㄢ","ㄔㄨㄢ","ㄕㄨㄢ","ㄖㄨㄢ","ㄗㄨㄢ","ㄘㄨㄢ","ㄙㄨㄢ","ㄨㄣ","ㄉㄨㄣ","ㄊㄨㄣ","ㄌㄨㄣ","ㄍㄨㄣ","ㄎㄨㄣ","ㄏㄨㄣ","ㄓㄨㄣ","ㄔㄨㄣ","ㄕㄨㄣ","ㄖㄨㄣ","ㄗㄨㄣ","ㄘㄨㄣ","ㄙㄨㄣ","ㄨㄤ","ㄍㄨㄤ","ㄎㄨㄤ","ㄏㄨㄤ","ㄓㄨㄤ","ㄔㄨㄤ","ㄕㄨㄤ","ㄨㄥ","ㄉㄨㄥ","ㄊㄨㄥ","ㄋㄨㄥ","ㄌㄨㄥ","ㄍㄨㄥ","ㄎㄨㄥ","ㄏㄨㄥ","ㄓㄨㄥ","ㄔㄨㄥ","ㄖㄨㄥ","ㄗㄨㄥ","ㄘㄨㄥ","ㄙㄨㄥ","ㄩ","ㄋㄩ","ㄌㄩ","ㄐㄩ","ㄑㄩ","ㄒㄩ","ㄩㄝ","ㄋㄩㄝ","ㄌㄩㄝ","ㄐㄩㄝ","ㄑㄩㄝ","ㄒㄩㄝ","ㄩㄢ","ㄐㄩㄢ","ㄑㄩㄢ","ㄒㄩㄢ","ㄩㄣ","ㄐㄩㄣ","ㄑㄩㄣ","ㄒㄩㄣ","ㄩㄥ","ㄐㄩㄥ","ㄑㄩㄥ","ㄒㄧㄥ"
    ).map { it.replace('一', 'ㄧ') }.toSet()

    fun getSuggestions(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val rawInput = input.replace('一', 'ㄧ')
        var normalized = rawInput
        TONE_MAP.forEach { (char, num) -> normalized = normalized.replace(char.toString(), num.toString()) }

        val segmentsInfo = splitPhoneticUnitsWithMapping(rawInput)
        val segments = segmentsInfo.map { it.first }
        if (segments.isEmpty()) return emptyList()

        val n = segments.size
        val candidatePool = mutableListOf<ScoredSuggestion>()
        val hasAnyTone = normalized.any { it in "01234" }

        // 取得首字母
        val initials = segments.map { it.filter { c -> c !in "01234" }.take(1) }.joinToString("")
        val baseLimit = if (n == 1) 1000 else if (hasAnyTone) 500 else 150

        // --- 核心改動：先檢查有無「全長字典詞」 ---
        val fullPhraseMatches = queryDictionaryWithRemainder(initials, segmentsInfo, rawInput, isNgram = 0, limit = baseLimit, useProximity = (n > 1))
        candidatePool.addAll(fullPhraseMatches)

        // 判斷是否需要執行 Viterbi：
        // 如果 candidatePool 裡面還沒有跟輸入長度一樣長的「基礎字典詞」，才考慮跑 Viterbi
        val hasFullLengthBaseWord = fullPhraseMatches.any { it.wordLen == n && it.isNgram == 0 }

        if (n > 1 && !hasFullLengthBaseWord) {
            val dp = Array(n + 1) { mutableListOf<ViterbiNode>() }
            dp[0].add(ViterbiNode("", 0.0, -1, 0, 0.0))
            for (i in 0 until n) {
                for (len in 1..segments.size - i) {
                    val matches = findExactMatchesFromDB(segments.subList(i, i + len), useProximity = true)
                    for (match in matches) {
                        val priorityBonus = if (match.isNgram == 0) 1000.0 else 0.0
                        val lengthBonus = (len - 1) * 50.0
                        val correctionPenalty = if (match.isFuzzy) PROXIMITY_PENALTY else 0.0
                        dp[i + len].add(ViterbiNode(match.word, match.weight + priorityBonus + lengthBonus + correctionPenalty, i, i + len))
                    }
                }
            }
            // DP 運算 ...
            for (idx in 1..n) {
                for (curr in dp[idx]) {
                    for (prev in dp[curr.startIdx]) {
                        val s = prev.totalScore + curr.score
                        if (s > curr.totalScore) { curr.totalScore = s; curr.bestPrev = prev }
                    }
                }
            }
            dp[n].maxByOrNull { it.totalScore }?.let { best ->
                val sentence = mutableListOf<String>(); var t: ViterbiNode? = best
                while (t != null && t.word.isNotEmpty()) { sentence.add(0, t.word); t = t.bestPrev }
                val fullWord = sentence.joinToString("")
                candidatePool.add(ScoredSuggestion("$fullWord|", fullWord.length, 999999.0, 0))
            }
        }

        // 補充第一個音節的單字 (連打時方便選取首字)
        if (n > 1) {
            val firstInit = segments[0].filter { it !in "01234" }.take(1)
            candidatePool.addAll(queryDictionaryWithRemainder(firstInit, listOf(segmentsInfo[0]), rawInput, isNgram = 0, limit = baseLimit, useProximity = false))
        }

        // N-gram 補全
        if (candidatePool.size < 20 || n > 1) {
            candidatePool.addAll(queryDictionaryWithRemainder(initials, segmentsInfo, rawInput, isNgram = 1, limit = 100, useProximity = (n > 1)))
        }

        // --- 最終結果排序：全長優先，長度由大到小 ---
        return candidatePool.distinctBy { it.display }
            .sortedWith(compareByDescending<ScoredSuggestion> { it.wordLen }
                .thenBy { it.isNgram }
                .thenByDescending { it.score })
            .map { it.display }
            .take(if (hasAnyTone) 300 else 40)
    }

    /**
     * 合法性糾錯邏輯 (保持不變)
     */
    private fun getSyllableVariants(segments: List<String>, useProximity: Boolean): List<String> {
        val originalInitials = segments.joinToString("") { it.filter { c -> c !in "01234" }.take(1) }
        val variants = mutableListOf(originalInitials)
        if (useProximity && segments.isNotEmpty()) {
            val firstSeg = segments.first()
            val firstChar = firstSeg.first()
            val firstSegVowel = firstSeg.filter { it !in "01234" }.substring(1)
            proximityMap[firstChar]?.forEach { neighbor ->
                val candidateSyllable = neighbor + firstSegVowel
                if (validSyllables.contains(candidateSyllable)) {
                    variants.add(neighbor + originalInitials.substring(1))
                }
            }
        }
        return variants
    }

    private fun queryDictionaryWithRemainder(
        initials: String,
        segmentsInfo: List<Pair<String, Int>>,
        rawInput: String,
        isNgram: Int,
        limit: Int,
        useProximity: Boolean
    ): List<ScoredSuggestion> {
        val list = mutableListOf<ScoredSuggestion>()
        val n = segmentsInfo.size
        val segments = segmentsInfo.map { it.first }

        val initialVariants = getSyllableVariants(segments, useProximity)
        val placeholders = initialVariants.joinToString(",") { "?" }
        val args = mutableListOf<String>()
        args.addAll(initialVariants)
        args.add(n.toString())
        args.add(isNgram.toString())
        args.add("${segments.first().filter { it !in "01234" }.take(1)}%")
        args.add(limit.toString())

        val sql = "SELECT word, syllables, length, initials, weight FROM dictionary " +
                "WHERE initials IN ($placeholders) AND length <= ? AND is_ngram = ? " +
                "AND syllables LIKE ? ORDER BY weight DESC LIMIT ?"

        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val dbSyllables = cursor.getString(1).split(",")
                val wordLen = cursor.getInt(2)
                val dbInit = cursor.getString(3)
                val baseWeight = cursor.getDouble(4)

                val isFuzzy = dbInit != initialVariants[0]
                if (checkPhoneticMatch(segments.take(wordLen), dbSyllables, isFuzzy)) {
                    val consumedRawLen = segmentsInfo.take(wordLen).sumOf { it.second }
                    val remainingRaw = rawInput.substring(consumedRawLen)
                    val finalWeight = if (isFuzzy) baseWeight + PROXIMITY_PENALTY else baseWeight
                    list.add(ScoredSuggestion("$word|$remainingRaw", wordLen, finalWeight, isNgram))
                }
            }
        }
        return list
    }

    private fun findExactMatchesFromDB(segments: List<String>, useProximity: Boolean): List<DbMatch> {
        val initialVariants = getSyllableVariants(segments, useProximity)
        val originalInitials = initialVariants[0]
        val list = mutableListOf<DbMatch>()
        val placeholders = initialVariants.joinToString(",") { "?" }
        val args = initialVariants.toMutableList()
        args.add(segments.size.toString())

        db.rawQuery(
            "SELECT word, weight, syllables, is_ngram, initials FROM dictionary WHERE initials IN ($placeholders) AND length = ? LIMIT 300",
            args.toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val word = cursor.getString(0); val weight = cursor.getDouble(1)
                val dbSyl = cursor.getString(2).split(","); val isNg = cursor.getInt(3)
                val dbInit = cursor.getString(4)
                val isFuzzy = dbInit != originalInitials
                if (checkPhoneticMatch(segments, dbSyl, isFuzzy)) {
                    list.add(DbMatch(word, weight, isNg, isFuzzy))
                }
            }
        }
        return list
    }

    private fun checkPhoneticMatch(inputSegments: List<String>, dbSyllables: List<String>, isFuzzy: Boolean): Boolean {
        if (inputSegments.size != dbSyllables.size) return false
        for (k in inputSegments.indices) {
            val inputSeg = inputSegments[k]
            val dbSeg = dbSyllables[k]
            if (k == 0 && isFuzzy) {
                val inputPure = inputSeg.filter { it !in "01234" }
                val dbPure = dbSeg.filter { it !in "01234" }
                if (inputPure.length > 1 && !dbPure.contains(inputPure.substring(1))) return false
            } else {
                val hasTone = inputSeg.any { it in "01234" }
                if (hasTone) {
                    if (inputSeg != dbSeg) return false
                } else {
                    val dbPure = dbSeg.filter { it !in "01234" }
                    if (!dbPure.startsWith(inputSeg)) return false
                }
            }
        }
        return true
    }

    private fun splitPhoneticUnitsWithMapping(rawInput: String): List<Pair<String, Int>> {
        var normalized = rawInput
        TONE_MAP.forEach { (char, num) -> normalized = normalized.replace(char.toString(), num.toString()) }
        val segments = splitPhoneticUnits(normalized)
        val result = mutableListOf<Pair<String, Int>>()
        var currentPos = 0
        segments.forEach { seg ->
            var rawLen = seg.filter { it !in "01234" }.length
            if (currentPos + rawLen < rawInput.length && rawInput[currentPos + rawLen] in TONE_MAP.keys) rawLen += 1
            result.add(Pair(seg, rawLen))
            currentPos += rawLen
        }
        return result
    }

    fun splitPhoneticUnits(input: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (i in input.indices) {
            if (input[i] in "01234") {
                val chunk = input.substring(start, i + 1)
                result.addAll(subSplitSyllables(chunk))
                start = i + 1
            }
        }
        if (start < input.length) result.addAll(subSplitSyllables(input.substring(start)))
        return result
    }

    private fun subSplitSyllables(chunk: String): List<String> {
        val subResult = mutableListOf<String>()
        val tone = chunk.find { it in "01234" }
        val content = chunk.filter { it !in "01234" }
        if (content.isEmpty()) return emptyList()
        var i = 0
        while (i < content.length) {
            var matchedLen = 0
            for (len in 4 downTo 1) {
                if (i + len <= content.length) {
                    val sub = content.substring(i, i + len)
                    if (validSyllables.contains(sub)) { subResult.add(sub); matchedLen = len; break }
                }
            }
            if (matchedLen > 0) i += matchedLen else { subResult.add(content[i].toString()); i++ }
        }
        if (tone != null && subResult.isNotEmpty()) {
            val lastIdx = subResult.size - 1
            subResult[lastIdx] = subResult[lastIdx] + tone
        }
        return subResult
    }
}