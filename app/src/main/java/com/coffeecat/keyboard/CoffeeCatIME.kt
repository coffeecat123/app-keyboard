package com.coffeecat.keyboard

import android.content.ClipDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.coffeecat.keyboard.data.BookmarkManager
import com.coffeecat.keyboard.data.SettingsManager
import com.coffeecat.keyboard.engine.BopomofoEngine
import com.coffeecat.keyboard.engine.EnglishEngine
import com.coffeecat.keyboard.providers.BopomofoProvider
import com.coffeecat.keyboard.providers.EnglishProvider
import com.coffeecat.keyboard.providers.LanguageProvider
import com.coffeecat.keyboard.view.BookmarkView
import com.coffeecat.keyboard.view.ClipboardView
import com.coffeecat.keyboard.view.EmojiPickerView
import com.coffeecat.keyboard.view.KeyAction
import com.coffeecat.keyboard.view.KeyType
import com.coffeecat.keyboard.view.KeyboardCodes
import com.coffeecat.keyboard.view.KeyboardMode
import com.coffeecat.keyboard.view.KeyboardView
import com.coffeecat.keyboard.view.ShiftState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoffeeCatIME : InputMethodService() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var englishProvider: EnglishProvider
    private var searchJob: Job? = null
    private val imeScope = kotlinx.coroutines.MainScope()
    private var composingText = StringBuilder()
    private var isPredictionEnabled = true // 預設開啟單字預測
    private val englishEngine = EnglishEngine(maxCandidatesPerNode = 10)
    private lateinit var bopomofoEngine: BopomofoEngine
    private lateinit var bopomofoProvider: BopomofoProvider
    private lateinit var suggestionBar: HorizontalScrollView
    private lateinit var suggestionContainer: LinearLayout
    private lateinit var suggestionArea: LinearLayout // 新增容器
    private lateinit var clearButton: View // 清除按鈕
    private lateinit var statusBubble: TextView
    private var isInteracting = false
    private var isSelectionModeActive = false
    private lateinit var container: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var keyboardWrapper: FrameLayout
    private lateinit var emojiPickerView: EmojiPickerView
    private lateinit var clipboardView: ClipboardView
    private lateinit var bookmarkView: BookmarkView
    private var selectionAnchor = -1
    override fun onCreate() {
        setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        bookmarkManager = BookmarkManager(this)
        super.onCreate()

        settingsManager = SettingsManager(this)
        // 初始化 Engine
        englishProvider = EnglishProvider(englishEngine)

        bopomofoEngine = BopomofoEngine(this)
        bopomofoProvider = BopomofoProvider(bopomofoEngine)

        Thread {
            loadEnglishDictionary()
        }.start()
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(Color.TRANSPARENT)
            //setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // 鍵盤包裝：改為全螢幕，透明
        keyboardWrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(Color.TRANSPARENT)

            // 鍵盤本體靠底
            keyboardView = KeyboardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                onAction = { action -> handleKeyAction(action) }
            }
            suggestionArea = LinearLayout(context).apply {
                val toolbarHeightPx = dpToPx(keyboardView.toolbarHeightDp.toInt())
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeightPx).apply {
                    gravity = Gravity.TOP
                }
                orientation = LinearLayout.HORIZONTAL
                visibility = View.GONE
                setBackgroundColor(Color.TRANSPARENT) // 之後在 setSuggestions 動態設顏色
            }

            // 建議詞捲動區域：寬度設為 0，weight 設為 1 (佔滿剩餘空間)
            suggestionBar = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                isHorizontalScrollBarEnabled = false
            }

            suggestionContainer = LinearLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            suggestionBar.addView(suggestionContainer)

            // 清除按鈕：固定在右邊
            clearButton = android.widget.ImageView(context).apply {
                val size = dpToPx(48) // 按鈕寬度
                layoutParams = LinearLayout.LayoutParams(size, ViewGroup.LayoutParams.MATCH_PARENT)
                setImageResource(R.drawable.rounded_cancel_24) // 請確保有這個圖標
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))

                // 設定點擊事件
                setOnClickListener {
                    val ic = currentInputConnection
                    ic.setComposingText("",1)
                    clearComposingText(ic)
                }
            }

            suggestionArea.addView(suggestionBar)
            suggestionArea.addView(clearButton)

            addView(keyboardView)
            addView(suggestionArea) // 加入容器而非原本的 suggestionBar
        }

        emojiPickerView = EmojiPickerView(this).apply {
            visibility = View.GONE
            // 這裡一定要 MATCH_PARENT 才能實現全螢幕滑動
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onEmojiSelected = { emoji -> currentInputConnection?.commitText(emoji, 1) }
            onBackPressed = { switchToKeyboardView() }
            onTouchStateChanged = { touching ->
                isInteracting = touching
                requestApplyInsets()
            }
        }

        clipboardView = ClipboardView(this).apply {
            visibility = View.GONE
            // 這裡一定要 MATCH_PARENT 才能實現全螢幕滑動
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onClipSelected = { clip -> currentInputConnection?.commitText(clip, 1) }
            onBackPressed = { switchToKeyboardView() }
            onTouchStateChanged = { touching ->
                isInteracting = touching
                requestApplyInsets()
            }
            onImageSelected = { localUri, mimeType ->
                // 呼叫我們之前討論過的 commitContent API
                doCommitContent("Clipboard Image", mimeType, localUri)
            }
            onAddBookmark = { text ->
                bookmarkManager.addBookmark(text)
                toastMessage("Added to bookmarks")
            }
            toastMessage = { message -> toastMessage(message) }
        }
        bookmarkView = BookmarkView(this).apply {
            visibility = View.GONE
            // 這裡一定要 MATCH_PARENT 才能實現全螢幕滑動
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onBookmarkSelected = { text -> currentInputConnection?.commitText(text, 1) }
            onBackPressed = { switchToKeyboardView() }
            onTouchStateChanged = { touching ->
                isInteracting = touching
                requestApplyInsets()
            }
            toastMessage = { message -> toastMessage(message) }
        }
        statusBubble = TextView(this).apply {
            val paddingH = dpToPx(16)
            val paddingV = dpToPx(8)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            alpha = 0f
            visibility = View.INVISIBLE

            background = android.graphics.drawable.GradientDrawable().apply {
                setColor("#CC000000".toColorInt())
                cornerRadius = dpToPx(20).toFloat()
            }

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(80)
            }
        }
        container.addView(keyboardWrapper)
        container.addView(emojiPickerView)
        container.addView(clipboardView)
        container.addView(bookmarkView)
        container.addView(statusBubble)
        container.post {
            updateAllLayoutParams()
        }
        return container
    }
    private fun setSuggestions(suggestions: List<String>) {
        suggestionBar.scrollTo(0, 0)
        suggestionContainer.removeAllViews()

        if (suggestions.isEmpty() && composingText.isEmpty()) {
            suggestionArea.visibility = View.GONE
            keyboardView.isToolbarHidden = false
            return
        }

        suggestionArea.visibility = View.VISIBLE
        keyboardView.isToolbarHidden = true
        (clearButton as? android.widget.ImageView)?.setColorFilter(keyboardView.cachedTextColor)

        suggestions.forEach { entry ->
            // 解析 "中文|剩餘注音"
            val parts = entry.split("|")
            val displayWord = parts[0]
            val remainingBopomofo = if (parts.size > 1) parts[1] else ""

            val tv = TextView(this).apply {
                text = displayWord // 只顯示中文
                textSize = 22f
                setTextColor(keyboardView.cachedTextColor)
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)

                setOnClickListener {
                    val ic = currentInputConnection ?: return@setOnClickListener

                    if (remainingBopomofo.isNotEmpty()) {
                        // --- 【分段選詞】 ---
                        // 1. 上字選中的詞
                        ic.commitText(displayWord, 1)

                        // 2. 更新 Composing 區域為剩下的「原始注音」
                        composingText.setLength(0)
                        composingText.append(remainingBopomofo)
                        ic.setComposingText(composingText, 1)

                        // 3. 觸發新注音的建議詞搜尋
                        updateSuggestionsWithRaw()
                    } else {
                        // --- 【整句選詞】 ---
                        val addSpace = keyboardView.currentMode == KeyboardMode.ALPHABET
                        ic.commitText(if (addSpace) "$displayWord " else displayWord, 1)
                        composingText.setLength(0)
                        setSuggestions(emptyList())
                    }
                }
            }
            suggestionContainer.addView(tv)
        }
    }
    private fun doCommitContent(description: String, mimeType: String, localUri: android.net.Uri) {
        val editorInfo = currentInputEditorInfo ?: return

        // 1. 檢查目標 App 是否支援此檔案類型
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)

        var isSupported = false
        for (type in mimeTypes) {
            if (ClipDescription.compareMimeTypes(mimeType, type)) {
                isSupported = true
                break
            }
        }

        if (!isSupported) {
            toastMessage("not supported")
            return
        }
        val file = java.io.File(localUri.path!!)
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        // 2. 封裝圖片資訊
        val inputContentInfo = InputContentInfoCompat(
            contentUri,
            ClipDescription(description, arrayOf(mimeType)),
            null // 可選：點擊圖片時跳轉的連結
        )

        // 3. 提交內容並請求臨時讀取權限
        InputConnectionCompat.commitContent(
            currentInputConnection,
            editorInfo,
            inputContentInfo,
            1,
            null
        )
    }
    private fun toastMessage(message: String) {
        showStatus(message)
    }
    private fun showStatus(message: String) {
        statusBubble.text = message
        statusBubble.visibility = View.VISIBLE

        statusBubble.animate().cancel()

        statusBubble.animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                statusBubble.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setStartDelay(800)
                    .withEndAction { statusBubble.visibility = View.INVISIBLE  }
                    .start()
            }
            .start()
    }
    private fun loadEnglishDictionary() {
        try {
            assets.open("english-20k.txt").bufferedReader().useLines { lines ->
                lines.forEach { word ->
                    // 如果檔案內只有單字，頻率預設為 1，之後可依據使用次數更新
                    englishEngine.insert(word.trim().lowercase())
                }
            }
        } catch (e: Exception) {
            Log.e("CoffeeCatIME", "讀取字典失敗: ${e.message}")
        }
    }
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
    private fun handleKeyAction(action: KeyAction) {
        val ic = currentInputConnection ?: return
        if (action.type == KeyType.TEXT || action.code == KeyboardCodes.DELETE) {
            if (isSelectionModeActive) {
                isSelectionModeActive = false
                keyboardView.setSelectionMode(false)
                selectionAnchor = -1
            }
        }

        ic.beginBatchEdit()
        try {
            when (action.type) {
                KeyType.SIGN ->{}
                KeyType.SPACER -> {}
                KeyType.TEXT -> {
                    val textBefore = ic.getTextBeforeCursor(10, 0)
                    if (textBefore.isNullOrEmpty() && composingText.isNotEmpty()) {
                        // 發現輸入框空了，但內部卻還有注音，強制重設
                        clearComposingText(ic)
                    }
                    val isAlphabetMode = keyboardView.currentMode != KeyboardMode.SYMBOLS &&
                            keyboardView.currentMode != KeyboardMode.NUMERIC
                    val isPunctuation = action.text.any { it in ",.，。" }
                    if (isPunctuation) {
                        // 1. 先把還在組合中的注音或英文上字
                        clearComposingText(ic)
                        // 2. 直接提交標點符號，不進 composing 區域
                        ic.commitText(action.text, 1)
                    } else if (isPredictionEnabled && isAlphabetMode) {
                        composingText.append(action.text)
                        ic.setComposingText(composingText, 1)

                        updateSuggestionsWithRaw()
                    }else{
                        ic.commitText(action.text, 1)
                    }

                    if (keyboardView.shiftState == ShiftState.SHIFTED) {
                        keyboardView.shiftState = ShiftState.UNSHIFTED
                        keyboardView.notifyShiftChanged()
                    }
                }
                KeyType.FUNCTION -> when (action.code) {
                    KeyboardCodes.LANGUAGE -> {
                        clearComposingText(ic)
                        if (keyboardView.baseLanguageMode == KeyboardMode.ALPHABET) {
                            toastMessage("中文 (注音)")
                            keyboardView.baseLanguageMode = KeyboardMode.BOPOMOFO
                        } else {
                            toastMessage("English (US)")
                            keyboardView.baseLanguageMode = KeyboardMode.ALPHABET
                        }
                        keyboardView.setMode(keyboardView.baseLanguageMode)
                        updateAllLayoutParams()
                    }
                    KeyboardCodes.DELETE -> {
                        if (ic.getSelectedText(0)?.isNotEmpty() == true) {
                            ic.commitText("", 1)
                        } else {
                            if (composingText.isNotEmpty()) {
                                composingText.deleteCharAt(composingText.length - 1)
                                ic.setComposingText(composingText, 1)
                                updateSuggestionsWithRaw()
                            } else {
                                sendKeyEventToApp(KeyEvent.KEYCODE_DEL)
                            }
                        }
                    }
                    KeyboardCodes.SHIFT -> keyboardView.handleShiftClick()
                    KeyboardCodes.MODE_ALPHABET, KeyboardCodes.MODE_NUMERIC,
                    KeyboardCodes.MODE_SYMBOL, KeyboardCodes.EDIT, KeyboardCodes.BACK -> {
                        clearComposingText(ic)
                        keyboardView.setMode(when(action.code) {
                            KeyboardCodes.MODE_NUMERIC -> KeyboardMode.NUMERIC
                            KeyboardCodes.MODE_SYMBOL -> KeyboardMode.SYMBOLS
                            KeyboardCodes.EDIT -> KeyboardMode.EDIT
                            else -> keyboardView.baseLanguageMode
                        })
                    }
                    KeyboardCodes.CURSOR_UP -> {
                        clearComposingText(ic)
                        if (isSelectionModeActive) sendShiftKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
                        else sendKeyEventToApp(KeyEvent.KEYCODE_DPAD_UP)
                    }
                    KeyboardCodes.CURSOR_DOWN -> {
                        clearComposingText(ic)
                        if (isSelectionModeActive) sendShiftKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
                        else sendKeyEventToApp(KeyEvent.KEYCODE_DPAD_DOWN)
                    }
                    KeyboardCodes.CURSOR_LEFT -> {
                        clearComposingText(ic)
                        if (isSelectionModeActive) sendShiftKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
                        else sendKeyEventToApp(KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                    KeyboardCodes.CURSOR_RIGHT -> {
                        clearComposingText(ic)
                        if (isSelectionModeActive) sendShiftKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
                        else sendKeyEventToApp(KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                    KeyboardCodes.HOME -> {
                        clearComposingText(ic)
                        if (isSelectionModeActive && selectionAnchor != -1) {
                            ic.setSelection(selectionAnchor, 0)
                        } else {
                            ic.setSelection(0, 0)
                        }
                    }
                    KeyboardCodes.END -> {
                        clearComposingText(ic)
                        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        if (extracted != null) {
                            val totalLength = extracted.text.length
                            if (isSelectionModeActive && selectionAnchor != -1) {
                                ic.setSelection(selectionAnchor, totalLength)
                            } else {
                                ic.setSelection(totalLength, totalLength)
                            }
                        }
                    }
                    KeyboardCodes.SELECT -> {
                        clearComposingText(ic)
                        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        val currentPos = extracted?.selectionEnd ?: 0

                        if (isSelectionModeActive) {
                            isSelectionModeActive = false
                            keyboardView.setSelectionMode(false)
                            selectionAnchor = -1
                            ic.setSelection(currentPos, currentPos)
                        } else {
                            isSelectionModeActive = true
                            keyboardView.setSelectionMode(true)
                            selectionAnchor = currentPos
                        }
                    }
                    KeyboardCodes.SELECT_ALL -> {
                        clearComposingText(ic)
                        isSelectionModeActive = false
                        keyboardView.setSelectionMode(false)
                        ic.performContextMenuAction(android.R.id.selectAll)
                    }
                    KeyboardCodes.COPY -> {
                        clearComposingText(ic)
                        if(ic.getSelectedText(0)?.isNotEmpty() == true) {
                            val clipboardManager =
                                getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val selectedText = ic.getSelectedText(0)
                            if (selectedText != null && selectedText.isNotEmpty()) {
                                val clip =
                                    android.content.ClipData.newPlainText("copied", selectedText)
                                clipboardManager.setPrimaryClip(clip)
                            }
                            isSelectionModeActive = false
                            keyboardView.setSelectionMode(false)
                            val extracted = ic.getExtractedText(
                                android.view.inputmethod.ExtractedTextRequest(),
                                0
                            )
                            if (extracted != null) {
                                val currentPos = extracted.selectionEnd
                                ic.setSelection(currentPos, currentPos)
                            }
                            toastMessage("Copied")
                        }
                        clipboardView.refreshClipboardData()
                    }
                    KeyboardCodes.CUT -> {
                        clearComposingText(ic)
                        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val selectedText = ic.getSelectedText(0)
                        if (selectedText != null && selectedText.isNotEmpty()) {
                            val clip = android.content.ClipData.newPlainText("copied", selectedText)
                            clipboardManager.setPrimaryClip(clip)
                            ic.commitText("", 1)
                        }
                        isSelectionModeActive = false
                        keyboardView.setSelectionMode(false)
                    }
                    KeyboardCodes.PASTE -> {
                        clearComposingText(ic)
                        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        if (clipboardManager.hasPrimaryClip()) {
                            val clip = clipboardManager.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pastedText = clip.getItemAt(0).text.toString()
                                ic.commitText(pastedText, 1)
                            }
                        }
                        isSelectionModeActive = false
                        keyboardView.setSelectionMode(false)
                    }
                }
                KeyType.ACTION -> when (action.code) {
                    KeyboardCodes.SPACE -> {
                        if(composingText.isEmpty()||keyboardView.currentMode== KeyboardMode.ALPHABET){
                            clearComposingText(ic)
                            ic.commitText(" ", 1)
                        }else {
                            val isAlphabetMode = keyboardView.currentMode != KeyboardMode.SYMBOLS &&
                                    keyboardView.currentMode != KeyboardMode.NUMERIC
                            if (isPredictionEnabled && isAlphabetMode) {
                                composingText.append(" ")
                                ic.setComposingText(composingText, 1)

                                updateSuggestionsWithRaw()
                            }
                        }
                    }
                    KeyboardCodes.ENTER -> handleEnterAction(ic)
                }
                KeyType.TOOLBAR -> {
                    clearComposingText(ic)
                    when (action.code) {
                        KeyboardCodes.PREDICT -> {
                            clearComposingText(ic)
                            isPredictionEnabled = !isPredictionEnabled
                            keyboardView.updatePrediction(isPredictionEnabled)
                        }
                        KeyboardCodes.BACK -> keyboardView.setMode(keyboardView.baseLanguageMode)
                        KeyboardCodes.HIDE -> requestHideSelf(0)
                        KeyboardCodes.SETTINGS -> openSettings()
                        KeyboardCodes.BOOKMARK ->{switchView(KeyboardMode.BOOKMARK)}
                        KeyboardCodes.EMOJI -> switchView(KeyboardMode.EMOJI)
                        KeyboardCodes.CLIPBOARD -> switchView(KeyboardMode.CLIPBOARD)
                        KeyboardCodes.EDIT -> keyboardView.setMode(KeyboardMode.EDIT)
                    }
                }
            }
        } finally {
            ic.endBatchEdit()
        }
    }
    private fun updateSuggestionsWithRaw() {
        searchJob?.cancel()
        val raw = composingText.toString()
        if (raw.isEmpty()) {
            setSuggestions(emptyList())
            return
        }

        // 1. 自動選擇 Provider
        val currentProvider: LanguageProvider = if (keyboardView.baseLanguageMode == KeyboardMode.BOPOMOFO) {
            bopomofoProvider
        } else {
            englishProvider
        }

        searchJob = imeScope.launch {
            val finalSuggestions = withContext(Dispatchers.Default) {
                // 2. 統一呼叫，不論中英文
                val results = currentProvider.getSuggestions(raw)

                // 3. 只有英文需要處理大小寫，注音不需要
                if (currentProvider is EnglishProvider) {
                    results.map { applyCase(it, raw) }.distinct().take(10)
                } else {
                    results // 中文直接回傳漢字列表
                }
            }
            setSuggestions(finalSuggestions)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        imeScope.cancel()
    }
    private fun applyCase(word: String, input: String): String {
        return when {
            // 1. 如果輸入全是全大寫，建議詞也全大寫 (例如打 "HELLO" -> 建議 "WORLD")
            input.length >= 2 && input.all { it.isUpperCase() } -> word.uppercase()

            // 2. 如果首字母是大寫，建議詞首字母也大寫 (例如打 "He" -> 建議 "Hello")
            input.isNotEmpty() && input[0].isUpperCase() -> {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            // 3. 其他情況回傳原始小寫
            else -> word
        }
    }
    private fun clearComposingText(ic: InputConnection) {
        if (composingText.isNotEmpty()) {
            ic.finishComposingText()
            composingText.setLength(0)
            setSuggestions(emptyList())
        }
    }
    private fun sendKeyEventToApp(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        ic.sendKeyEvent(downEvent)
        ic.sendKeyEvent(upEvent)
    }
    private fun sendShiftKeyEvent(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
    }
    private fun sendCtrlKeyEvent(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,keyCode,0,KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
        ic.sendKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_UP,keyCode,0,KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
    }
    private fun switchView(keyboardMode: KeyboardMode){
        keyboardView.dismissPreview()
        keyboardWrapper.visibility = View.GONE
        emojiPickerView.visibility = View.GONE
        clipboardView.visibility = View.GONE
        bookmarkView.visibility = View.GONE

        keyboardView.currentMode=keyboardMode
        when(keyboardMode){
            KeyboardMode.EMOJI -> {
                emojiPickerView.visibility = View.VISIBLE
                emojiPickerView.scrollToTop()
            }
            KeyboardMode.CLIPBOARD -> {
                clipboardView.visibility = View.VISIBLE
                clipboardView.scrollToTop()
                clipboardView.refreshClipboardData()
            }
            KeyboardMode.BOOKMARK -> {
                bookmarkView.visibility = View.VISIBLE
                bookmarkView.scrollToTop()
                bookmarkView.refreshBookmarkData()
            }
            else -> {}
        }
    }
    private fun switchToKeyboardView() {
        keyboardWrapper.visibility = View.VISIBLE
        emojiPickerView.visibility = View.GONE
        clipboardView.visibility = View.GONE
        bookmarkView.visibility = View.GONE
        keyboardView.setMode(keyboardView.baseLanguageMode)
    }

    private fun handleEnterAction(ic: InputConnection){
        clearComposingText(ic)
        val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val ic = currentInputConnection
        if (ic != null) {
            // 1. 確保呼叫你已經寫好的清除函式
            // 這會執行 ic.finishComposingText(), composingText.setLength(0), 並隱藏建議欄
            clearComposingText(ic)
        }
        keyboardView.setMode(keyboardView.baseLanguageMode)
        isSelectionModeActive = false
        keyboardView.setSelectionMode(false)
        selectionAnchor = -1
        keyboardView.applyStyles(settingsManager)
        switchToKeyboardView()
        info?.let { keyboardView.updateEnterKeyLabel(it.imeOptions) }
    }
    @Suppress("DEPRECATION")
    override fun onConfigureWindow(win: android.view.Window, isFullscreen: Boolean, isCandidatesIndices: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesIndices)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
        win.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        win.setDimAmount(0f)
        win.statusBarColor = Color.TRANSPARENT
        win.navigationBarColor = Color.TRANSPARENT
    }
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!::keyboardView.isInitialized || !::emojiPickerView.isInitialized) return

        val totalHeight = container.height
        val contentHeight=keyboardView.getMeasuredKeyboardHeightPx()
        val contentTop=totalHeight-contentHeight

        outInsets.contentTopInsets = contentTop
        outInsets.visibleTopInsets = contentTop
        outInsets.touchableInsets = if (isInteracting) Insets.TOUCHABLE_INSETS_FRAME else Insets.TOUCHABLE_INSETS_CONTENT
    }
    private fun updateAllLayoutParams() {
        if (!::keyboardView.isInitialized || !::emojiPickerView.isInitialized) return

        val newHeight = keyboardView.getMeasuredKeyboardHeightPx()

        // 1. 更新鍵盤本體高度
        val kbParams = keyboardView.layoutParams
        if (kbParams != null && kbParams.height != newHeight) {
            kbParams.height = newHeight
            keyboardView.layoutParams = kbParams
        }
        suggestionArea.layoutParams.height = dpToPx(keyboardView.toolbarHeightDp.toInt())
        emojiPickerView.updateTargetHeight(newHeight)
        clipboardView.updateTargetHeight(newHeight)
        bookmarkView.updateTargetHeight(newHeight)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateAllLayoutParams()

        container.post {
            container.requestLayout()
        }
    }
}