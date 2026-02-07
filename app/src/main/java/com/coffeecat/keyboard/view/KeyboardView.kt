package com.coffeecat.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.coffeecat.keyboard.R
import com.coffeecat.keyboard.data.KeyboardLayouts
import com.coffeecat.keyboard.data.SettingsManager

enum class KeyType {
    TEXT, FUNCTION, ACTION, TOOLBAR,SIGN,SPACER
}
enum class ShiftState {
    UNSHIFTED, SHIFTED, SHIFT_LOCKED
}
data class KeyAction(
    val code: String,      // 按鍵代碼
    val type: KeyType,    // 按鍵類型
    val text: String      // 最終要輸入的文字（處理過大小寫後）
)
enum class KeyboardMode {
    ALPHABET,   // 字母 QWERTY
    BOPOMOFO,   // 注音 (測試用)
    NUMERIC,    // 數字與常用符號
    SYMBOLS,    // 進階符號 (=\<...)
    EMOJI,      // 表情符號
    EDIT,
    SETTINGS,
    CLIPBOARD,
    BOOKMARK
}
object KeyboardCodes {
    // 功能鍵
    const val DELETE = "DEL"
    const val SHIFT = "SHIFT"
    const val LANGUAGE = "LANG"
    const val MODE_SYMBOL = "=\\<"
    const val MODE_NUMERIC = "?123"
    const val MODE_ALPHABET = "ABC"
    const val MODE_BOPOMOFO = "注音"

    // 動作鍵
    const val SPACE = "SPC"
    const val ENTER = "ENT"

    // Toolbar
    const val PREDICT = "PRED"
    const val EMOJI = "EMOJI"
    const val SETTINGS = "SET"
    const val HIDE = "HIDE"
    const val CLIPBOARD = "CLIP"
    const val EDIT = "EDIT"
    const val BACK = "BACK"
    const val BOOKMARK = "BOOKMARK"

    const val CURSOR_UP = "CURSOR_UP"
    const val CURSOR_DOWN = "CURSOR_DOWN"
    const val CURSOR_LEFT = "CURSOR_LEFT"
    const val CURSOR_RIGHT = "CURSOR_RIGHT"
    const val SELECT = "SELECT"
    const val SELECT_ALL = "SELECT_ALL"
    const val COPY = "COPY"
    const val CUT = "CUT"
    const val PASTE = "PASTE"
    const val HOME = "HOME"
    const val END = "END"
}
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var onAction: ((KeyAction) -> Unit)? = null
    var currentMode = KeyboardMode.ALPHABET

    private var backgroundBitmap: android.graphics.Bitmap? = null
    private var backgroundImageAlpha: Float = 1.0f
    private var userShowButton = true // 對應你的 showButton
    private var userShowOutline = true // 對應你的 showButtonOutline
    private var userShowText=true
    var baseLanguageMode = KeyboardMode.ALPHABET
    private var isPredictionEnabled = true // 預設開啟單字預測
    private var cachedBgColor = 0
    private var cachedKeyColor = 0
    private var cachedFunctionalKeyColor = 0
    private var cachedFunctionalTextColor = 0 // 專門給功能鍵（Icon 和文字）使用的淡色
    private var cachedToolbarColor = 0
    var cachedTextColor = 0
    private var cachedAccentColor = 0
    private var cachedPressedMaskColor = 0
    private var iconSizePx = 0
    var shiftState = ShiftState.UNSHIFTED // 取代原本的 isShifted
    private var lastShiftPressTime = 0L
    private val doubleTapTimeout = 300L // 雙擊判定時間（毫秒）
    private val activePointers = mutableMapOf<Int, PointerState>()
    private var previewPointerId: Int = -1
    private val iconCache = mutableMapOf<String, Drawable?>()
    private var currentImeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    var isToolbarHidden: Boolean = false
        set(value) {
            field = value
            invalidate() // 狀態改變時重繪
        }
    private fun getIconForCode(code: String): Drawable? {
        iconCache[code]?.let { return it }

        val resId = when (code) {
            KeyboardCodes.DELETE -> R.drawable.rounded_backspace_24
            KeyboardCodes.SHIFT -> when (shiftState) {
                ShiftState.UNSHIFTED -> R.drawable.rounded_shift_24        // 外框箭頭
                ShiftState.SHIFTED -> R.drawable.rounded_shift_filled_24  // 實心箭頭
                ShiftState.SHIFT_LOCKED -> R.drawable.rounded_shift_lock_24 // 帶底線的實心箭頭
            }
            KeyboardCodes.CURSOR_LEFT -> R.drawable.rounded_arrow_left_alt_24
            KeyboardCodes.CURSOR_RIGHT -> R.drawable.rounded_arrow_right_alt_24
            KeyboardCodes.CURSOR_UP -> R.drawable.rounded_arrow_upward_24
            KeyboardCodes.CURSOR_DOWN -> R.drawable.rounded_arrow_downward_24
            KeyboardCodes.BACK -> R.drawable.rounded_keyboard_arrow_left_24
            // 修改：根據 Action 回傳不同的 Enter 圖標
            KeyboardCodes.ENTER -> when (currentImeAction) {
                EditorInfo.IME_ACTION_DONE -> R.drawable.rounded_check_24   // 完成
                EditorInfo.IME_ACTION_SEARCH -> R.drawable.rounded_search_24 // 搜尋
                EditorInfo.IME_ACTION_SEND -> R.drawable.rounded_send_24     // 傳送
                EditorInfo.IME_ACTION_GO -> R.drawable.rounded_arrow_right_alt_24         // 前往
                EditorInfo.IME_ACTION_NEXT -> R.drawable.rounded_keyboard_arrow_right_24     // 下一個
                else -> R.drawable.rounded_keyboard_return_24                // 預設換行
            }
            KeyboardCodes.PREDICT ->{
                if(isPredictionEnabled) R.drawable.predict
                else R.drawable.no_predict
            }
            KeyboardCodes.BOOKMARK -> R.drawable.rounded_bookmark_24
            KeyboardCodes.HIDE -> R.drawable.rounded_keyboard_hide_24
            KeyboardCodes.SETTINGS -> R.drawable.rounded_settings_24
            KeyboardCodes.LANGUAGE -> R.drawable.rounded_language_24
            KeyboardCodes.EMOJI -> R.drawable.rounded_mood_24
            KeyboardCodes.CLIPBOARD -> R.drawable.rounded_assignment_24
            KeyboardCodes.EDIT -> R.drawable.rounded_text_editing_24
            else -> null
        }
        val icon = if (resId != null) ContextCompat.getDrawable(context, resId) else null
        if (icon != null) iconCache[code] = icon
        return icon
    }
    private fun sendAction(key: KeyItem, overrideText: String? = null) {
        val action = KeyAction(
            code = key.code,
            type = key.type,
            text = overrideText ?: getDisplayLabel(key)
        )
        onAction?.invoke(action)
    }
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var activeRepeatKeyItem: KeyItem? = null // 改為儲存 KeyItem
    private var isRepeatTriggered = false

    private val initialRepeatDelay = 350L // 第一次按下去到開始「連發」的等待時間 (原本 500 較慢)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            activeRepeatKeyItem?.let { key ->
                isRepeatTriggered = true
                sendAction(key)

                // 如果是方向鍵，間隔稍微長一點 (例如 80ms)，刪除鍵維持原速 (50ms)
                val nextInterval = if (key.code == KeyboardCodes.DELETE) 50L else 80L
                repeatHandler.postDelayed(this, nextInterval)
            }
        }
    }
    private val previewHeightPx = dpToPx(56f).toInt() // 固定高度，例如 64dp
    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val toolbarHeightDp: Float
        get() = if (isLandscape) 40f else 48f  // 橫向時 Toolbar 縮小

    private val buttonHeightDp: Float
        get() = if (isLandscape) 35f else 40f  // 橫向時按鈕高度縮小

    private val spacingDp: Float
        get() = if (isLandscape) 4f else 10f    // 橫向時垂直間距縮小
    // 氣泡預覽組件
    private var previewPopup: PopupWindow? = null
    private val previewText: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 24f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false

        background = GradientDrawable().apply {
            cornerRadius = dpToPx(28f) // 圓角
        }

        // 左右給 padding，上下不需要，因為我們要固定高度並居中
        val px20 = dpToPx(8f).toInt()
        setPadding(px20, 0, px20, 0)

        // 設定固定高度
        height = previewHeightPx
        minWidth = dpToPx(48f).toInt()
    }

    private var isSelectionMode = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val textPaintSIGN = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private data class KeyItem(
        var label: String,
        val code: String,
        val type: KeyType,
        val weight: Float, // 新增 weight
        val rect: RectF = RectF(),
        val hitRect: RectF = RectF()
    )
    private val dismissRunnable = Runnable {
        previewText.alpha = 0f
        previewPointerId = -1
    }
    private val keyList = mutableListOf<KeyItem>()
    private data class PointerState(
        val key: KeyItem,
        val initialKey: KeyItem,
        var isCommitted: Boolean = false
    )
    init {
        applyStyles(SettingsManager(context))
        setMode(baseLanguageMode)
        iconSizePx = dpToPx(24f).toInt()

        // 初始化 PopupWindow
        previewPopup = PopupWindow(previewText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isClippingEnabled = false
            elevation = 15f
            // 關鍵：讓它不要阻擋觸控
            isTouchable = false
            animationStyle = 0
        }
        loadLayout()
    }
    fun applyStyles(settings: SettingsManager) {
        // 1. 同步基礎設定
        userShowOutline = settings.showOutline
        // 假設你在 SettingsManager 也有存這個，如果沒有就先用預設
        userShowButton = settings.showButton
        userShowText = settings.showText
        cachedAccentColor = settings.accentColor
        cachedKeyColor = settings.keyColor

        cachedBgColor = settings.backgroundColor
        cachedTextColor = settings.textColor

        cachedToolbarColor = settings.toolbarColor
        cachedFunctionalKeyColor = settings.functionKeyColor

        cachedFunctionalTextColor = settings.functionTextColor

        cachedPressedMaskColor = ColorUtils.setAlphaComponent(cachedTextColor, 30)


        backgroundImageAlpha = settings.backgroundImageAlpha
        val path = settings.backgroundImagePath

        backgroundBitmap = if (path != null) {
            try {
                // 建議：實際開發中可加入圖片縮放(InSampleSize)優化記憶體
                android.graphics.BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        // 2. 更新字體
        val userTypeface = settings.getTypeface()
        textPaint.typeface = userTypeface
        textPaintSIGN.typeface = Typeface.create(userTypeface, Typeface.BOLD)


        // 氣泡預覽顏色同步
        updatePreviewStyles()

        invalidate()
    }
    private fun updatePreviewStyles() {
        val previewBgColor = ColorUtils.blendARGB(cachedKeyColor, Color.GRAY, 0.4f)
        val previewTextColor = if (ColorUtils.calculateLuminance(previewBgColor) > 0.5) Color.BLACK else Color.WHITE

        previewText.setTextColor(previewTextColor)
        (previewText.background as? GradientDrawable)?.apply {
            setColor(previewBgColor)
            setStroke(2, ColorUtils.setAlphaComponent(previewTextColor, 20))
        }
    }
    fun setMode(mode: KeyboardMode) {
        currentMode = mode
        shiftState = ShiftState.UNSHIFTED
        notifyShiftChanged()

        // 重新載入對應模式的按鍵清單
        loadLayout()

        // 立即根據目前的 View 高度重新計算按鍵坐標
        if (width > 0 && height > 0) {
            recomputeKeyRects(width, height)
        }

        invalidate() // 僅重繪
    }
    fun setSelectionMode(active: Boolean) {
        isSelectionMode = active
        invalidate() // 重新繪製
    }
    private fun loadLayout() {
        keyList.clear()
        val layout = KeyboardLayouts.getLayout(currentMode)
        layout.forEach { row ->
            row.forEach { def ->
                keyList.add(KeyItem(def.label, def.code, def.type, def.weight))
            }
        }
        // 使用目前的寬高立即計算一次，防止縮在左上角
        if (width > 0 && height > 0) {
            recomputeKeyRects(width, height)
        }
    }

    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)


    fun updateEnterKeyLabel(imeOptions: Int) {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        currentImeAction = action // 儲存當前 Action

        iconCache.remove(KeyboardCodes.ENTER)

        val newLabel = when (action) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            else -> "ENTER"
        }

        keyList.find { it.code == KeyboardCodes.ENTER }?.label = newLabel
        invalidate()
    }
    fun getMeasuredKeyboardHeightPx(): Int {
        val baseLayout = KeyboardLayouts.getLayout(baseLanguageMode)
        val rowCount = baseLayout.size // 取得該語言主頁的總列數

        val toolbarH = dpToPx(toolbarHeightDp)
        val gap = dpToPx(spacingDp)
        val btnH = dpToPx(buttonHeightDp)

        // 總高度 = Toolbar高度 + (其餘按鈕列數 * 標準按鈕高度) + (間距)
        return (toolbarH + (rowCount - 1) * btnH + rowCount * gap).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 強制使用計算出來的基準高度
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getMeasuredKeyboardHeightPx())
    }
    private fun recomputeKeyRects(w: Int, h: Int) {
        if (keyList.isEmpty() || w <= 0 || h <= 0) return

        val currentLayout = KeyboardLayouts.getLayout(currentMode)
        val currentRowsCount = currentLayout.size
        val toolbarH = dpToPx(toolbarHeightDp)
        val bottomH = dpToPx(buttonHeightDp)
        val gap = dpToPx(spacingDp)

        val isEditMode = currentMode == KeyboardMode.EDIT
        val dynamicBtnH = if (isEditMode) {
            val available = h - toolbarH - (currentRowsCount * gap)
            if (currentRowsCount > 1) available / (currentRowsCount - 1) else 0f
        } else {
            val available = h - toolbarH - bottomH - (currentRowsCount * gap)
            val middleRowsCount = currentRowsCount - 2
            if (middleRowsCount > 0) available / middleRowsCount else 0f
        }

        var keyCounter = 0
        var currentTop = 0f

        currentLayout.forEachIndexed { rowIndex, row ->
            val isFirst = rowIndex == 0
            val isLast = rowIndex == currentRowsCount - 1
            val rowHeight = when {
                isFirst -> toolbarH
                isEditMode -> dynamicBtnH
                isLast -> bottomH
                else -> dynamicBtnH
            }
            val rowTop = currentTop
            val rowBottom = rowTop + rowHeight
            val hitRowBottom = if (isLast) h.toFloat() + dpToPx(20f) else rowBottom + gap

            var thisRowTotalWeight = 0f
            for (i in row.indices) {
                if (keyCounter + i < keyList.size) {
                    thisRowTotalWeight += keyList[keyCounter + i].weight
                }
            }
            val rowUnitW = if (thisRowTotalWeight > 0f) w / thisRowTotalWeight else 0f

            var leftmostNormalIdx = -1
            var rightmostNormalIdx = -1
            for (i in row.indices) {
                if (keyCounter + i < keyList.size && keyList[keyCounter + i].type != KeyType.SPACER) {
                    if (leftmostNormalIdx == -1) leftmostNormalIdx = i
                    rightmostNormalIdx = i
                }
            }

            var currentX = 0f
            row.forEachIndexed { colIndex, _ ->
                if (keyCounter >= keyList.size) return@forEachIndexed
                val itemKey = keyList[keyCounter]
                val keyW = rowUnitW * itemKey.weight

                val padding = dpToPx(2f)
                itemKey.rect.set(
                    currentX + (if (isFirst) 0f else padding),
                    rowTop,
                    currentX + keyW - (if (isFirst) 0f else padding),
                    rowBottom
                )

                if (itemKey.type == KeyType.SPACER) {
                    itemKey.hitRect.setEmpty()
                } else {
                    itemKey.hitRect.set(
                        if (colIndex == leftmostNormalIdx) 0f else currentX,
                        rowTop,
                        if (colIndex == rightmostNormalIdx) w.toFloat() else currentX + keyW,
                        hitRowBottom
                    )
                }
                currentX += keyW
                keyCounter++
            }
            currentTop = rowBottom + gap
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyRects(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cachedBgColor) // 使用緩存顏色
        backgroundBitmap?.let { bitmap ->
            paint.alpha = (backgroundImageAlpha * 255).toInt()

            // 計算 CenterCrop 邏輯：將圖片等比例縮放以填滿 View 寬高
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            val scale = (viewWidth / bitmapWidth).coerceAtLeast(viewHeight / bitmapHeight)
            val drawW = bitmapWidth * scale
            val drawH = bitmapHeight * scale
            val left = (viewWidth - drawW) / 2f
            val top = 0f

            // 建立目標矩形
            val dstRect = RectF(left, top, left + drawW, top + drawH)

            canvas.drawBitmap(bitmap, null, dstRect, paint)

            // 恢復 paint 的 alpha 供後續繪製使用
            paint.alpha = 255
        }
        paint.color = cachedToolbarColor
        val toolbarHeight = dpToPx(toolbarHeightDp)
        canvas.drawRect(0f, 0f, width.toFloat(), toolbarHeight, paint)

        keyList.forEach { key ->
            if (key.type == KeyType.SPACER) return@forEach // 關鍵：Spacer 不繪製
            if (isToolbarHidden && key.type == KeyType.TOOLBAR) {
                return@forEach
            }
            val icon = getIconForCode(key.code)

            // 繪製按鍵背景 (只有非 TOOLBAR 的按鍵才有獨立背景框)
            if(key.type != KeyType.SIGN){
                if (key.type != KeyType.TOOLBAR) {
                    paint.style = Paint.Style.FILL // 預設填充
                    paint.color = when {
                        key.type == KeyType.ACTION -> cachedAccentColor
                        key.code == KeyboardCodes.SHIFT && shiftState != ShiftState.UNSHIFTED -> cachedAccentColor
                        key.code == KeyboardCodes.SELECT && isSelectionMode -> cachedAccentColor
                        key.type == KeyType.FUNCTION -> cachedFunctionalKeyColor
                        else -> cachedKeyColor
                    }
                    if(key.type != KeyType.TEXT) {
                        canvas.drawRoundRect(key.rect, 12f, 12f, paint)
                    }else if(userShowButton){
                        canvas.drawRoundRect(key.rect, 12f, 12f, paint)
                    }
                    if (userShowOutline) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = dpToPx(1f)
                        paint.color = ColorUtils.setAlphaComponent(cachedTextColor, 40) // 淡淡的外框
                        canvas.drawRoundRect(key.rect, 12f, 12f, paint)
                        paint.style = Paint.Style.FILL // 設回填充模式供下次使用
                    }
                    if (isKeyPressed(key)) {
                        paint.color = cachedPressedMaskColor
                        canvas.drawRoundRect(key.rect, 12f, 12f, paint)
                    }
                } else {
                    // 如果是 TOOLBAR 按鍵被按下，也給一個淡淡的圓形或圓角矩形回饋
                    if (isKeyPressed(key)) {
                        paint.color = cachedPressedMaskColor
                        canvas.drawRoundRect(key.rect, 12f, 12f, paint)
                    }
                }
            }

            // 2. 繪製內容
            if (icon != null) {
                val left = key.rect.centerX().toInt() - iconSizePx / 2
                val top = key.rect.centerY().toInt() - iconSizePx / 2
                icon.setBounds(left, top, left + iconSizePx, top + iconSizePx)
                val iconTint = if (key.type == KeyType.FUNCTION && !(key.code == KeyboardCodes.SHIFT && shiftState != ShiftState.UNSHIFTED)) {
                    cachedFunctionalTextColor // 功能鍵用淡色
                } else {
                    cachedTextColor // 字母鍵或 Action 鍵（Enter/Space）或 啟用的 Shift 用亮色
                }

                icon.setTint(iconTint)
                icon.draw(canvas)
            } else {
                textPaint.color = if (key.type == KeyType.FUNCTION) cachedFunctionalTextColor else cachedTextColor
                textPaint.textSize = dpToPx(if (key.type == KeyType.FUNCTION) 20f else 24f)

                val displayLabel = getDisplayLabel(key)
                val centerX= key.rect.centerX()
                val centerY = key.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                if(key.type == KeyType.SIGN){
                    textPaintSIGN.color = cachedTextColor
                    textPaintSIGN.textSize = dpToPx(24f)
                    canvas.drawText(displayLabel, key.rect.left, centerY, textPaintSIGN)
                }else if(key.type== KeyType.TEXT){
                    if(userShowText||currentMode!=baseLanguageMode)
                        canvas.drawText(displayLabel, centerX, centerY, textPaint)
                }else{
                    canvas.drawText(displayLabel, centerX, centerY, textPaint)
                }
            }
        }
    }
    fun notifyShiftChanged() {
        iconCache.remove(KeyboardCodes.SHIFT)
        invalidate()
    }
    fun updatePrediction(b: Boolean){
        iconCache.remove(KeyboardCodes.PREDICT)
        isPredictionEnabled = b
        invalidate()
    }
    private fun isKeyPressed(key: KeyItem): Boolean {
        // 1. 如果是文字鍵，送出即熄滅 (Rollover 體驗)
        // 2. 如果是功能鍵，只要手指還在上面就亮著 (標準按鈕體驗)
        return activePointers.values.any {
            it.key == key && (it.key.type != KeyType.TEXT || !it.isCommitted)
        }
    }
    private fun getDisplayLabel(key: KeyItem): String {
        return if (shiftState != ShiftState.UNSHIFTED && key.type == KeyType.TEXT && currentMode==KeyboardMode.ALPHABET) {
            key.label.uppercase()
        } else {
            key.label
        }
    }
    fun handleShiftClick() {
        val currentTime = System.currentTimeMillis()
        shiftState = if (currentTime - lastShiftPressTime < doubleTapTimeout) {
            // 觸發雙擊：鎖定大寫
            ShiftState.SHIFT_LOCKED
        } else {
            // 觸發單擊：在小寫、大寫之間切換
            if (shiftState == ShiftState.UNSHIFTED) ShiftState.SHIFTED else ShiftState.UNSHIFTED
        }
        lastShiftPressTime = currentTime
        notifyShiftChanged()
    }
    private fun showPreview(pointerId: Int, key: KeyItem, label: String) {
        if (key.type != KeyType.TEXT) return

        previewPointerId = pointerId
        previewText.text = label

        previewText.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(previewHeightPx, MeasureSpec.EXACTLY)
        )

        val pw = previewText.measuredWidth + 20
        val ph = previewHeightPx

        val location = IntArray(2)
        getLocationInWindow(location)

        val x = location[0] + key.rect.centerX().toInt() - (pw / 2)
        val y = location[1] + key.rect.top.toInt() - ph - dpToPx(12f).toInt()

        val screenWidth = resources.displayMetrics.widthPixels
        val margin = dpToPx(8f).toInt()
        val finalX = x.coerceIn(margin, screenWidth - pw - margin)

        // --- 核心修復：防止飛過去的邏輯 ---
        if (previewPopup?.isShowing == true && previewText.alpha > 0f) {
            // 快速連續打字或滑動中：使用 update 保持高性能
            previewPopup?.update(finalX, y, pw, ph)
        } else {
            // 第一下按下，或者停頓一段時間後：
            // 先關閉再立即打開，這能強制氣泡「瞬移」到新位置，不會有移動動畫
            previewPopup?.dismiss()
            previewPopup?.width = pw
            previewPopup?.height = ph
            previewPopup?.showAtLocation(this, Gravity.NO_GRAVITY, finalX, y)
        }

        previewText.alpha = 1.0f
    }
    fun dismissPreview() {
        repeatHandler.removeCallbacks(dismissRunnable)
        previewPopup?.dismiss()
        previewPointerId = -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 立即切斷所有消失任務，釋放 UI 資源
                repeatHandler.removeCallbacks(dismissRunnable)

                // 強制清理可能殘留的舊 ID 數據
                activePointers.remove(pointerId)

                // Rollover：強制送出舊文字
                activePointers.forEach { (id, state) ->
                    if (state.key.type == KeyType.TEXT && !state.isCommitted) {
                        sendAction(state.key)
                        state.isCommitted = true
                        if (previewPointerId == id) previewPointerId = -1
                    }
                }
                val key = keyList.find {
                    it.hitRect.contains(x, y) && !(isToolbarHidden && it.type == KeyType.TOOLBAR)
                }
                if (key != null) {
                    activePointers[pointerId] = PointerState(
                        key = key,
                        initialKey = key,
                        isCommitted = false
                    )
                    if (key.type == KeyType.TEXT) {
                        showPreview(pointerId, key, getDisplayLabel(key))
                    }else if (key.type == KeyType.TOOLBAR) {
                        // --- 修改這裡：TOOLBAR 在按下時不執行 sendAction ---
                        // 讓 isCommitted 保持預設的 false，這樣 ACTION_UP 就會偵測到它
                    }else {
                        sendAction(key)
                        hidePreviewDelayed()
                        if (isRepeatable(key.code)) {
                            isRepeatTriggered = false
                            activeRepeatKeyItem = key
                            repeatHandler.postDelayed(repeatRunnable, initialRepeatDelay)
                        }else {
                            activePointers[pointerId]?.isCommitted = true
                        }
                    }
                    performClick()
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)
                    val state = activePointers[pid] ?: continue

                    // --- 關鍵優化：如果這隻手指已經送出過了，無視它的移動 ---
                    if (state.isCommitted) continue

                    val currentKey = keyList.find {
                        it.hitRect.contains(x, y) && !(isToolbarHidden && it.type == KeyType.TOOLBAR)
                    }
                    if (currentKey != state.key) {
                        if (isRepeatable(state.key.code)) stopRepeat()

                        if (currentKey != null) {
                            // 滑動更新：只有在還沒 Committed 的情況下才允許換鍵
                            activePointers[pid] = PointerState(
                                key = currentKey,
                                initialKey = state.initialKey,
                                isCommitted = false
                            )
                            if (currentKey.type == KeyType.TEXT) {
                                repeatHandler.removeCallbacks(dismissRunnable) // 移動時也確保不被 dismiss
                                showPreview(pid, currentKey, getDisplayLabel(currentKey))
                            } else if (previewPointerId == pid) {
                                hidePreviewDelayed(0)
                            }
                        } else {
                            activePointers.remove(pid)
                            if (previewPointerId == pid) hidePreviewDelayed(0)
                        }
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val state = activePointers[pointerId]
                if (state != null) {
                    val moved = state.key.type != state.initialKey.type
                    // --- 關鍵優化：如果沒被送出過（正常單擊），才在抬起時送出 ---
                    if (!state.isCommitted&&(!moved||state.key.type == KeyType.TEXT)) {
                        if ((state.key.type == KeyType.TEXT || state.key.type == KeyType.TOOLBAR) && !isRepeatTriggered) {
                            sendAction(state.key)
                        }
                        if (isRepeatable(state.key.code)) stopRepeat()
                    }
                    // 標記為已送出，防止任何殘留邏輯
                    state.isCommitted = true
                }

                // 氣泡跳轉邏輯：只跳轉到「還按著」且「尚未送出」的文字鍵
                if (previewPointerId == pointerId) {
                    val nextPointer = activePointers.entries.find {
                        it.key != pointerId &&
                                it.value.key.type == KeyType.TEXT &&
                                !it.value.isCommitted
                    }

                    if (nextPointer != null) {
                        showPreview(nextPointer.key, nextPointer.value.key, getDisplayLabel(nextPointer.value.key))
                    } else {
                        hidePreviewDelayed()
                    }
                }

                activePointers.remove(pointerId)
                if (activePointers.isEmpty()) {
                    isRepeatTriggered = false
                    // 如果所有手指都離開了，確保氣泡最終一定會消失
                    hidePreviewDelayed(100)
                }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                activePointers.clear()
                hidePreviewDelayed(0)
                isRepeatTriggered = false
                invalidate()
            }
        }
        return true
    }
    private fun isRepeatable(code: String): Boolean {
        return code == KeyboardCodes.DELETE ||
                code == KeyboardCodes.CURSOR_UP ||
                code == KeyboardCodes.CURSOR_DOWN ||
                code == KeyboardCodes.CURSOR_LEFT ||
                code == KeyboardCodes.CURSOR_RIGHT
    }
    private fun hidePreviewDelayed(delay: Long =40) {
        repeatHandler.removeCallbacks(dismissRunnable)
        if (delay == 0L) {
            previewText.alpha = 0f
            previewPointerId = -1
        } else {
            repeatHandler.postDelayed(dismissRunnable, delay)
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        invalidate()
    }
    private fun stopRepeat() {
        activeRepeatKeyItem = null // 清空儲存的按鍵
        isRepeatTriggered = false // 停止時必須重置，否則會影響下一次點擊判定
        repeatHandler.removeCallbacks(repeatRunnable)
    }

    override fun performClick(): Boolean {
        playSoundEffect(SoundEffectConstants.CLICK)
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        repeatHandler.removeCallbacksAndMessages(null) // 清空所有待執行任務
        previewPopup?.dismiss()
        backgroundBitmap?.recycle()
        backgroundBitmap = null
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            // 失去焦點時，強行重置所有狀態，防止按鍵「卡住」
            stopRepeat()
            activePointers.clear()
            hidePreviewDelayed(0)
            invalidate()
        }
    }
}