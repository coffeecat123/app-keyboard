package com.coffeecat.keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class EmojiPickerView(context: Context) : LinearLayout(context) {

    var onEmojiSelected: ((String) -> Unit)? = null
    var onBackPressed: (() -> Unit)? = null

    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    private var targetHeightPx: Int = 0
    // 將常數移到這裡
    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EMOJI = 1
    }
    private val contentContainer: LinearLayout

    private val recyclerView: RecyclerView
    private val bottomBar: HorizontalScrollView
    private val adapter: EmojiAdapter

    private sealed class EmojiListItem {
        data class Header(val name: String) : EmojiListItem()
        data class Emoji(val char: String) : EmojiListItem()
    }

    // 擴充後的分類資料
    private val categories = listOf(
        Pair("最近使用", listOf("😂", "❤️", "🤣", "👍", "🙏", "✨", "😊", "🔥", "😭", "🥰")),
        Pair("表情", (0x1F600..0x1F637).map { String(Character.toChars(it)) }),
        Pair("手勢", (0x1F446..0x1F450).map { String(Character.toChars(it)) }),
        Pair("食物", (0x1F32D..0x1F350).map { String(Character.toChars(it)) }),
        Pair("自然", (0x1F330..0x1F350).map { String(Character.toChars(it)) }),
        Pair("活動", (0x1F3A0..0x1F3C4).map { String(Character.toChars(it)) }),
        Pair("1", (0x1F446..0x1F450).map { String(Character.toChars(it)) }),
        Pair("2", (0x1F32D..0x1F350).map { String(Character.toChars(it)) }),
        Pair("3", (0x1F330..0x1F350).map { String(Character.toChars(it)) }),
        Pair("4", (0x1F3A0..0x1F3C4).map { String(Character.toChars(it)) })
    )

    private val flatList: List<EmojiListItem> = categories.flatMap { (name, emojis) ->
        listOf(EmojiListItem.Header(name)) + emojis.map { EmojiListItem.Emoji(it) }
    }
    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    fun getMeasuredEmojiHeightPx(): Int {
        val dp = if (isLandscape) 200f else 248f // 橫屏時 Emoji 視窗縮短
        return dpToPx(dp.toInt())
    }
    private fun updateLayoutHeights() {
        // 確保 contentContainer 的 LayoutParams 類型正確
        val params = contentContainer.layoutParams
        params.height = getMeasuredEmojiHeightPx()
        contentContainer.layoutParams = params

        // 橫向時縮小內部 Padding
        recyclerView.setPadding(0, dpToPx(if (isLandscape) 4 else 8), 0, dpToPx(4))

        // 強制重新佈局
        requestLayout()
    }

    init {
        orientation = VERTICAL
        // 根容器必須是全螢幕且透明
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)

        // 加入一個彈簧，把內容推到底部
        addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            // 關鍵：這個 Spacer 不能攔截觸摸，否則平時也無法穿透
            isClickable = false
            isFocusable = false
        })

        contentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(getThemeColor(R.attr.colorSurfaceVariant))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            // 攔截內容區域的觸摸，防止穿透到下層 App
            setOnTouchListener { _, _ -> true }
        }
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(16), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(if(isLandscape) 40 else 48))
            setBackgroundColor(ColorUtils.setAlphaComponent(getThemeColor(R.attr.colorOnSurface), 15))
        }

        val backBtn = ImageButton(context).apply {
            setImageResource(com.coffeecat.keyboard.R.drawable.rounded_keyboard_arrow_left_24)
            setColorFilter(getThemeColor(R.attr.colorOnSurface))
            background = getSelectableItemBackgroundResource(true)
            setOnClickListener { onBackPressed?.invoke() }
            // 讓按鈕稍微寬一點點，比較好點
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
        }

        val title = TextView(context).apply {
            text = "Emoji"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
            setTextColor(getThemeColor(R.attr.colorOnSurface))
            setPadding(dpToPx(12), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbar.addView(backBtn)
        toolbar.addView(title)
        contentContainer.addView(toolbar)

        // 2. RecyclerView (Emoji 列表)
        adapter = EmojiAdapter()
        val gridLayoutManager = GridLayoutManager(context, 8)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.getItemViewType(position) == TYPE_HEADER) 8 else 1
        }

        recyclerView = RecyclerView(context).apply {
            layoutManager = gridLayoutManager
            itemAnimator = null
            this.adapter = this@EmojiPickerView.adapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            clipToPadding = false
            setPadding(0, dpToPx(8), 0, dpToPx(8))
            // 確保滾動時不會有奇怪的邊界顏色
            overScrollMode = OVER_SCROLL_NEVER
        }
        contentContainer.addView(recyclerView)
        this.isFocusable = true
        this.isFocusableInTouchMode = true

        // 確保 RecyclerView 的父容器不攔截事件
        recyclerView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        val bottomBarColor = ColorUtils.setAlphaComponent(getThemeColor(R.attr.colorOnSurface),30)
        // 3. Category Bar (底部切換列)
        bottomBar = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(56))
            isHorizontalScrollBarEnabled = false
            // --- 修改：底部欄使用跟 KeyboardView Toolbar 類似的半透明遮罩效果 ---
            setBackgroundColor(bottomBarColor)
        }

        val categoryContainer = LinearLayout(context).apply { orientation = HORIZONTAL }
        val categoryIcons = listOf("🕒", "😊", "👋", "🍕", "🌲", "⚽", "🚗", "💡", "💖", "🏁")

        categories.forEachIndexed { index, pair ->
            val btn = TextView(context).apply {
                text = categoryIcons.getOrElse(index) { "⭐" }
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(dpToPx(52), LayoutParams.MATCH_PARENT)
                background = getSelectableItemBackgroundResource(false)
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    val position = flatList.indexOfFirst { it is EmojiListItem.Header && it.name == pair.first }
                    if (position != -1) {
                        (recyclerView.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0)
                    }
                }
            }
            categoryContainer.addView(btn)
        }
        bottomBar.addView(categoryContainer)
        contentContainer.addView(bottomBar)

        addView(contentContainer)
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> onTouchStateChanged?.invoke(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchStateChanged?.invoke(false)
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }
    fun updateTargetHeight(heightPx: Int) {
        targetHeightPx = heightPx
        val params = contentContainer.layoutParams
        params.height = heightPx
        contentContainer.layoutParams = params
        requestLayout()
    }

    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        bottomBar.scrollTo(0, 0)
    }
    fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateLayoutHeights()
    }
    // 獲取點擊效果的 Helper (Ripple)
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getSelectableItemBackgroundResource(borderless: Boolean): Drawable? {
        val typedValue = TypedValue()
        val attribute = if (borderless) {
            android.R.attr.selectableItemBackgroundBorderless
        } else {
            // 使用有邊界的漣漪，看起來會比較小且被限制在 View 內
            android.R.attr.selectableItemBackground
        }
        context.theme.resolveAttribute(attribute, typedValue, true)
        return context.getDrawable(typedValue.resourceId)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private inner class EmojiAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (flatList[position]) {
                is EmojiListItem.Header -> TYPE_HEADER
                is EmojiListItem.Emoji -> TYPE_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(parent.context).apply {
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(4))
                    // 分類標題用主色調 (Primary)
                    setTextColor(getThemeColor(R.attr.colorPrimary))
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                }
                HeaderVH(tv)
            } else {
                val tv = TextView(parent.context).apply {
                    textSize = 28f
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
                    background = getSelectableItemBackgroundResource(false)
                    isClickable = true
                    isFocusable = true
                }
                EmojiVH(tv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flatList[position]
            if (holder is HeaderVH && item is EmojiListItem.Header) {
                holder.tv.text = item.name
            } else if (holder is EmojiVH && item is EmojiListItem.Emoji) {
                holder.tv.text = item.char
                holder.tv.setOnClickListener { onEmojiSelected?.invoke(item.char) }
            }
        }
        override fun getItemCount(): Int = flatList.size
        inner class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        inner class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    }
}