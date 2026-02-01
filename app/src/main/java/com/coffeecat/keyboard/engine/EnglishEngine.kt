package com.coffeecat.keyboard.engine

import kotlin.collections.iterator
import kotlin.text.iterator

class EnglishEngine(private val maxCandidatesPerNode: Int = 5) {
    class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        // 儲存以該節點為前綴的最佳單字
        val topCandidates = mutableListOf<String>()
        // 標記該節點本身是否為一個完整單字
        var isWord: Boolean = false
    }

    private val root = TrieNode()

    fun insert(word: String) {
        val lowerWord = word.trim().lowercase()
        if (lowerWord.isEmpty()) return

        var curr = root
        for (char in lowerWord) {
            curr = curr.children.getOrPut(char) { TrieNode() }
            if (lowerWord !in curr.topCandidates) {
                curr.topCandidates.add(lowerWord)
                if (curr.topCandidates.size > maxCandidatesPerNode) {
                    curr.topCandidates.removeAt(curr.topCandidates.size - 1)
                }
            }
        }
        curr.isWord = true
    }

    /**
     * 優化後的模糊搜尋
     */
    fun getFuzzySuggestions(input: String, maxDistance: Int = 1): List<String> {
        val searchInput = input.lowercase()
        if (searchInput.isEmpty()) return emptyList()

        // 為了效能，動態決定容許的距離
        val limit = when {
            searchInput.length <= 2 -> 0
            searchInput.length <= 4 -> 1
            else -> maxDistance
        }

        // 結果集：Word -> Distance
        val results = mutableMapOf<String, Int>()

        // 初始列向量 (0, 1, 2, ..., n)
        val currentRow = IntArray(searchInput.length + 1) { it }

        // 從根節點的子節點開始深度優先搜尋
        for ((char, node) in root.children) {
            searchRecursiveOptimized(
                node, char, searchInput, currentRow, results, limit
            )
        }

        return results.entries
            .sortedWith(compareBy({ it.value }, { it.key.length }))
            .map { it.key }
            .take(15)
    }

    private fun searchRecursiveOptimized(
        node: TrieNode,
        char: Char,
        target: String,
        prevRow: IntArray,
        results: MutableMap<String, Int>,
        maxDistance: Int
    ) {
        val size = target.length
        val currentRow = IntArray(size + 1)
        currentRow[0] = prevRow[0] + 1

        var minRowValue = currentRow[0]

        // 計算當前列的值 (Wagner-Fischer 演算法)
        for (i in 1..size) {
            val insertCost = currentRow[i - 1] + 1
            val deleteCost = prevRow[i] + 1
            val replaceCost = if (target[i - 1] == char) prevRow[i - 1] else prevRow[i - 1] + 1

            currentRow[i] = minOf(insertCost, minOf(deleteCost, replaceCost))

            if (currentRow[i] < minRowValue) minRowValue = currentRow[i]
        }

        // 剪枝：如果這一行所有值都大於 maxDistance，代表此路不通
        if (minRowValue > maxDistance) return

        // 如果最後一個元素小於等於上限，代表「前綴」匹配成功
        // 我們將該節點下所有預存的候選詞加入結果
        if (currentRow[size] <= maxDistance) {
            node.topCandidates.forEach { word ->
                // 存入最小的編輯距離
                val currentBest = results[word] ?: (maxDistance + 1)
                if (currentRow[size] < currentBest) {
                    results[word] = currentRow[size]
                }
            }
        }

        // 繼續往下搜尋子節點
        for ((nextChar, nextNode) in node.children) {
            searchRecursiveOptimized(nextNode, nextChar, target, currentRow, results, maxDistance)
        }
    }

    // 精確匹配保持不變，因為它非常快
    fun getSuggestions(prefix: String): List<String> {
        val searchPrefix = prefix.lowercase()
        var curr = root
        for (char in searchPrefix) {
            curr = curr.children[char] ?: return emptyList()
        }
        return curr.topCandidates.toList()
    }
}