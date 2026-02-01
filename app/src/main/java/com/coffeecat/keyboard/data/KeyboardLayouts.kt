package com.coffeecat.keyboard.data

import com.coffeecat.keyboard.view.KeyType
import com.coffeecat.keyboard.view.KeyboardCodes
import com.coffeecat.keyboard.view.KeyboardMode

object KeyboardLayouts {

    // --- DSL 輔助工具 ---
    private fun sign(char: String, weight: Float = 1.0f) = KeyDef(char, KeyType.SIGN, char, weight)
    private fun spacer(weight: Float = 1.0f) = KeyDef("", KeyType.SPACER, "", weight)
    private fun text(char: String, weight: Float = 1.0f) = KeyDef(char, KeyType.TEXT, char, weight)
    private fun action(code: String, label: String? = null, weight: Float = 1.5f) =
        KeyDef(code, KeyType.ACTION, label ?: code, weight)
    private fun func(code: String, label: String? = null, weight: Float = 1.5f) =
        KeyDef(code, KeyType.FUNCTION, label ?: code, weight)


    private fun tool(code: String,w:Float =1f) = KeyDef(code, KeyType.TOOLBAR, code, w)

    private fun back(w: Float = 1f) = tool(KeyboardCodes.BACK, w)
    private fun shift(w: Float = 1.5f) = func(KeyboardCodes.SHIFT, null, w)
    private fun delete(w: Float = 1.5f) = func(KeyboardCodes.DELETE, null, w)
    private fun enter(w: Float = 1.5f) = action(KeyboardCodes.ENTER, null, w)
    private fun space(w: Float = 4.0f) = action(KeyboardCodes.SPACE, "", w)
    private fun lang(w: Float = 1.0f) = func(KeyboardCodes.LANGUAGE, "", w)

    // 模式切換按鍵
    private fun toNumeric(label: String = "?123", w: Float = 1.5f) = func(KeyboardCodes.MODE_NUMERIC, label, w)
    private fun toSymbols(label: String = "=\\<", w: Float = 1.5f) = func(KeyboardCodes.MODE_SYMBOL, label, w)
    private fun toAlphabet(label: String = "ABC", w: Float = 1.5f) = func(KeyboardCodes.MODE_ALPHABET, label, w)

    // --- 工具列 (Toolbar) ---
    private val standardToolbar = listOf(
        tool(KeyboardCodes.PREDICT,2f),
        tool(KeyboardCodes.EMOJI,2f),
        tool(KeyboardCodes.CLIPBOARD,2f),
        tool(KeyboardCodes.EDIT,2f),
        tool(KeyboardCodes.BOOKMARK,2f),
        tool(KeyboardCodes.SETTINGS,2f),
        tool(KeyboardCodes.HIDE,2f)
    )
    // --- 注音佈局 (測試 6 列效果) ---
    val BOPOMOFO: KeyboardLayout = listOf(
        standardToolbar,
        "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ".map { text(it.toString()) },
        listOf(spacer(0.5f))+"ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ".map { text(it.toString()) }+listOf(spacer(0.5f)),
        listOf(spacer(0.5f))+"ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ".map { text(it.toString()) }+listOf(spacer(0.5f)),
        "ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ".map { text(it.toString()) } + listOf(delete()),
        listOf(
            toNumeric(),
            text("，"),
            space(),
            text("。"),
            lang(),
            enter()
        )
    )
    // --- QWERTY 佈局 ---
    val QWERTY: KeyboardLayout = listOf(
        standardToolbar,
        listOf(spacer(0.1f))+"qwertyuiop".map { text(it.toString()) }+listOf(spacer(0.1f)),
        listOf(spacer(0.6f)) + "asdfghjkl".map { text(it.toString()) } + listOf(spacer(0.6f)),
        listOf(shift()) + "zxcvbnm".map { text(it.toString()) } + listOf(delete()),
        listOf(
            toNumeric(),
            text(","),
            space(),
            text("."),
            lang(),
            enter()
        )
    )

    // --- NUMERIC 佈局 ---
    val NUMERIC: KeyboardLayout = listOf(
        standardToolbar,
        listOf(spacer(0.1f))+"1234567890".map { text(it.toString()) }+listOf(spacer(0.1f)),
        listOf(spacer(0.1f))+"@#\$_&-+()/".map { text(it.toString()) }+listOf(spacer(0.1f)),
        listOf(toSymbols()) + "*\"':;!?".map { text(it.toString()) } + listOf(delete()),
        listOf(
            toAlphabet(),
            text(","),
            space(),
            text("."),
            lang(),
            enter()
        )
    )

    // --- SYMBOLS 佈局 ---
    val SYMBOLS: KeyboardLayout = listOf(
        standardToolbar,
        listOf(spacer(0.1f))+"~`|•√π÷×§∆".map { text(it.toString()) }+listOf(spacer(0.1f)),
        listOf(spacer(0.1f))+"£¢€¥^°={}\\".map { text(it.toString()) }+listOf(spacer(0.1f)),
        listOf(toNumeric()) + "%©®™✓[]".map { text(it.toString()) } + listOf(delete()),
        listOf(
            toAlphabet(),
            text("<"),
            space(),
            text(">"),
            lang(),
            enter()
        )
    )
    // --- EDIT 佈局 ---
    val EDIT: KeyboardLayout = listOf(
        listOf(
            back(1.3f),
            sign("",0.2f),
            sign("Text editing",8.5f),
        ),
        listOf(
            sign("",2.5f),
            func(KeyboardCodes.CURSOR_UP, "↑", 2.5f),
            sign("",2.5f),
            func(KeyboardCodes.DELETE, "⌫", 2.5f)
        ),
        listOf(
            func(KeyboardCodes.CURSOR_LEFT, "←", 2.5f),
            func(KeyboardCodes.SELECT, "Select", 2.5f),
            func(KeyboardCodes.CURSOR_RIGHT, "→", 2.5f),
            func(KeyboardCodes.COPY, "Copy", 2.5f)
        ),
        listOf(
            sign("",2.5f),
            func(KeyboardCodes.CURSOR_DOWN, "↓", 2.5f),
            sign("",2.5f),
            func(KeyboardCodes.CUT, "Cut", 2.5f)
        ),
        listOf(
            func(KeyboardCodes.HOME, "Home", 2.5f),
            func(KeyboardCodes.SELECT_ALL, "Select All", 2.5f),
            func(KeyboardCodes.END, "End", 2.5f),
            func(KeyboardCodes.PASTE, "Paste", 2.5f)
        )
    )
    // 根據模式取得佈局
    fun getLayout(mode: KeyboardMode): KeyboardLayout {
        return when (mode) {
            KeyboardMode.ALPHABET -> QWERTY
            KeyboardMode.BOPOMOFO -> BOPOMOFO
            KeyboardMode.NUMERIC -> NUMERIC
            KeyboardMode.SYMBOLS -> SYMBOLS
            KeyboardMode.EDIT -> EDIT
            else -> QWERTY
        }
    }
}