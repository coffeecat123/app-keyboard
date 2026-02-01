package com.coffeecat.keyboard.data

import com.coffeecat.keyboard.view.KeyType

// 定義單個按鍵的屬性
data class KeyDef(
    val code: String,
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1.0f
) {
    // 輔助建構子：如果 label 沒給，就跟 code 一樣
    constructor(code: String, type: KeyType, weight: Float = 1.0f) :
            this(code, type, code, weight)
}

// 定義一整行
typealias KeyboardRow = List<KeyDef>
// 定義一個完整的模式佈局
typealias KeyboardLayout = List<KeyboardRow>