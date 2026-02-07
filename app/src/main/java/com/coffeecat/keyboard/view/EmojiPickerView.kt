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
import com.coffeecat.keyboard.data.SettingsManager
import java.io.File

@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class EmojiPickerView(
    context: Context,
    private val settings: SettingsManager
) : LinearLayout(context) {
    // 定義顏色變數以便後續使用
    private val bgColor = settings.backgroundColor
    private val textColor = settings.textColor
    private val toolbarColor = settings.toolbarColor
    private val userTypeface = settings.getTypeface()

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
    private var categories: List<Pair<String, List<String>>> = emptyList()
    private var flatList: List<EmojiListItem> = emptyList()
    // 擴充後的分類資料
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
            setBackgroundColor(bgColor)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            // 攔截內容區域的觸摸，防止穿透到下層 App
            setOnTouchListener { _, _ -> true }
        }
        updateBackground(contentContainer, settings)
        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(16), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(if(isLandscape) 40 else 48))
            setBackgroundColor(toolbarColor)
        }

        val backBtn = ImageButton(context).apply {
            setImageResource(com.coffeecat.keyboard.R.drawable.rounded_keyboard_arrow_left_24)
            setColorFilter(textColor)
            background = getSelectableItemBackgroundResource(true)
            setOnClickListener { onBackPressed?.invoke() }
            // 讓按鈕稍微寬一點點，比較好點
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
        }

        val title = TextView(context).apply {
            text = "Emoji"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = userTypeface
            includeFontPadding = false
            setTextColor(textColor)
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
        val bottomBarColor = ColorUtils.setAlphaComponent(bgColor,30)
        // 3. Category Bar (底部切換列)
        bottomBar = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(56))
            isHorizontalScrollBarEnabled = false
            // --- 修改：底部欄使用跟 KeyboardView Toolbar 類似的半透明遮罩效果 ---
            setBackgroundColor(bottomBarColor)
        }
        refreshEmojiData()
        val categoryContainer = LinearLayout(context).apply {
            tag = "category_container" // 給個標籤方便稍後尋找
            orientation = HORIZONTAL
        }
        bottomBar.addView(categoryContainer)
        contentContainer.addView(bottomBar)
        updateCategoryBar()

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
    private fun updateCategoryBar() {
        // 找到 categoryContainer (你可以把它存成 class 變數，或者透過 tag 找)
        val container = bottomBar.getChildAt(0) as? LinearLayout ?: return
        container.removeAllViews()

        categories.forEach { pair ->
            val categoryName = pair.first
            val emojiList = pair.second

            val icon = if (categoryName == "最近使用") {
                "🕒"
            } else {
                emojiList.firstOrNull() ?: "⭐"
            }

            val btn = TextView(context).apply {
                text = icon
                setTextColor(textColor)
                typeface = Typeface.DEFAULT_BOLD
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(dpToPx(52), LayoutParams.MATCH_PARENT)
                background = getSelectableItemBackgroundResource(false)
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    val position = flatList.indexOfFirst { it is EmojiListItem.Header && it.name == categoryName }
                    if (position != -1) {
                        (recyclerView.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0)
                    }
                }
            }
            container.addView(btn)
        }
    }
    fun emojiRange(start: Int, end: Int) = (start..end).map { String(Character.toChars(it)) }
    private fun refreshEmojiData() {
        val recent = getRecentEmojis()
        val baseCategories = mutableListOf<Pair<String, List<String>>>()
        baseCategories.addAll(listOf(

            // 1. 最近使用
            Pair("最近使用", recent),

            // 2. 笑脸与人物（含手势、身体部位）
            Pair("笑臉與人物",
                emojiRange(0x1F600, 0x1F64F) +          // 经典表情
                        emojiRange(0x1F910, 0x1F93E) +          // 补充表情与人物
                        emojiRange(0x1F9D1, 0x1F9DD) +          // 多肤色人物
                        emojiRange(0x1F466, 0x1F478) +          // 儿童与成人
                        emojiRange(0x1F479, 0x1F480) +          // 幻想人物（鬼、骷髅等）
                        emojiRange(0x1F481, 0x1F487) +          // 手势与美容
                        listOf("\u263A", "\u2639")              // ☺ ☹
            ),

            // 3. 动物与自然
            Pair("動物與自然",
                emojiRange(0x1F400, 0x1F43F) +          // 哺乳动物、鸟类等
                        emojiRange(0x1F440, 0x1F441) +          // 眼睛（常归入自然）
                        emojiRange(0x1F330, 0x1F39F) +          // 植物、天气
                        emojiRange(0x1F980, 0x1F99F) +          // 新增动物（恐龙、鲨鱼等）
                        emojiRange(0x1F9A0, 0x1F9BF)            // 昆虫、植物补充
            ),

            // 4. 食物与饮料
            Pair("食物與飲料",
                emojiRange(0x1F347, 0x1F37F) +          // 水果、甜点、饮料
                        emojiRange(0x1F32D, 0x1F32F) +          // 热狗、玉米
                        emojiRange(0x1F950, 0x1F96F) +          // 蔬菜、餐具
                        emojiRange(0x1F9C0, 0x1F9CF) +          // 奶酪、面包、冰淇淋
                        emojiRange(0x1F9D0, 0x1F9DF).filter { it.codePointCount(0, it.length) == 1 } // 过滤非食物（如思考脸）
            ),

            // 5. 活动与运动
            Pair("活動與運動",
                emojiRange(0x1F3A0, 0x1F3C4) +          // 游戏、球类
                        emojiRange(0x1F3C6, 0x1F3CA) +          // 奖杯、游泳
                        emojiRange(0x1F3CB, 0x1F3FF).filter { it.codePointCount(0, it.length) == 1 } + // 健身、音乐
                        emojiRange(0x1F93C, 0x1F93E)            // 摔跤、篮球（补充）
            ),

            // 6. 旅行与地点
            Pair("旅行與地點",
                emojiRange(0x1F300, 0x1F32F) +          // 天气、星空
                        emojiRange(0x1F3D4, 0x1F3DF) +          // 山脉、地图
                        emojiRange(0x1F3E0, 0x1F3EF) +          // 建筑
                        emojiRange(0x1F680, 0x1F6FF).filter { it.codePointCount(0, it.length) == 1 } // 交通工具
            ),

            // 7. 物品与日常
            Pair("物品與日常",
                emojiRange(0x1F380, 0x1F38F) +          // 礼物、气球
                        emojiRange(0x1F390, 0x1F39F) +          // 音乐物品
                        emojiRange(0x1F488, 0x1F48E) +          // 工具、宝石
                        emojiRange(0x1F4A0, 0x1F4A9) +          // 日常用品
                        emojiRange(0x1F4AB, 0x1F4AF) +          // 星星、爆炸
                        emojiRange(0x1F4B0, 0x1F4B9) +          // 钱包、货币
                        emojiRange(0x1F4F0, 0x1F4FF) +          // 电子设备
                        emojiRange(0x1F500, 0x1F53F) +          // 工具、时钟
                        emojiRange(0x1F540, 0x1F54F) +          // 宗教物品
                        emojiRange(0x1F550, 0x1F567)            // 时钟面
            ),

            // 8. 符号与标志
            Pair("符號與標誌",
                emojiRange(0x2600, 0x26FF) +            // 杂项符号（太阳、心形等）
                        emojiRange(0x2700, 0x27BF) +            // 装饰符号（剪刀、星星）
                        listOf("\u231A", "\u231B", "\u2328", "\u23CF", "\u23E9", "\u23EA", "\u23EB", "\u23EC", "\u23F0", "\u23F3", "\u25FD", "\u25FE", "\u2600", "\u2601", "\u2602", "\u2603", "\u2604", "\u2614", "\u2615", "\u2618", "\u261D", "\u2620", "\u2622", "\u2623", "\u2626", "\u262A", "\u262E", "\u262F", "\u2638", "\u2639", "\u263A", "\u2648", "\u2649", "\u264A", "\u264B", "\u264C", "\u264D", "\u264E", "\u264F", "\u2650", "\u2651", "\u2652", "\u2653", "\u2660", "\u2663", "\u2665", "\u2666", "\u2668", "\u267B", "\u267F", "\u2692", "\u2693", "\u2694", "\u2696", "\u2697", "\u2699", "\u269B", "\u269C", "\u26A0", "\u26A1", "\u26AA", "\u26AB", "\u26B0", "\u26B1", "\u26BD", "\u26BE", "\u26C4", "\u26C5", "\u26C8", "\u26CE", "\u26CF", "\u26D1", "\u26D3", "\u26D4", "\u26E9", "\u26EA", "\u26F0", "\u26F1", "\u26F2", "\u26F3", "\u26F4", "\u26F5", "\u26F7", "\u26F8", "\u26F9", "\u26FA", "\u26FD", "\u2702", "\u2708", "\u2709", "\u270C", "\u270D", "\u270F", "\u2712", "\u2714", "\u2716", "\u271D", "\u2721", "\u2728", "\u2733", "\u2734", "\u2744", "\u2747", "\u274C", "\u274E", "\u2753", "\u2754", "\u2755", "\u2757", "\u2763", "\u2764", "\u2795", "\u2796", "\u2797", "\u27A1", "\u27B0", "\u27BF")
            ),

            // 旗幟 (Flags)
            Pair("旗幟", listOf(
                "🇦🇨", "🇦🇩", "🇦🇪", "🇦🇫", "🇦🇬", "🇦🇮", "🇦🇱", "🇦🇲", "🇦🇴", "🇦🇶",
                "🇦🇷", "🇦🇸", "🇦🇹", "🇦🇺", "🇦🇼", "🇦🇽", "🇦🇿", "🇧🇦", "🇧🇧", "🇧🇩",
                "🇧🇪", "🇧🇫", "🇧🇬", "🇧🇭", "🇧🇮", "🇧🇯", "🇧🇱", "🇧🇲", "🇧🇳", "🇧🇴",
                "🇧🇶", "🇧🇷", "🇧🇸", "🇧🇹", "🇧🇻", "🇧🇼", "🇧🇾", "🇧🇿", "🇨🇦", "🇨🇨",
                "🇨🇩", "🇨🇫", "🇨🇬", "🇨🇭", "🇨🇮", "🇨🇰", "🇨🇱", "🇨🇲", "🇨🇳", "🇨🇴",
                "🇨🇵", "🇨🇷", "🇨🇺", "🇨🇻", "🇨🇼", "🇨🇽", "🇨🇾", "🇨🇿", "🇩🇪", "🇩🇬",
                "🇩🇯", "🇩🇰", "🇩🇲", "🇩🇴", "🇩🇿", "🇪🇦", "🇪🇨", "🇪🇪", "🇪🇬", "🇪🇭",
                "🇪🇷", "🇪🇸", "🇪🇹", "🇪🇺", "🇫🇮", "🇫🇯", "🇫🇰", "🇫🇲", "🇫🇴", "🇫🇷",
                "🇬🇦", "🇬🇧", "🇬🇩", "🇬🇪", "🇬🇫", "🇬🇬", "🇬🇭", "🇬🇮", "🇬🇱", "🇬🇲",
                "🇬🇳", "🇬🇵", "🇬🇶", "🇬🇷", "🇬🇸", "🇬🇹", "🇬🇺", "🇬🇼", "🇬🇾", "🇭🇰",
                "🇭🇲", "🇭🇳", "🇭🇷", "🇭🇹", "🇭🇺", "🇮🇨", "🇮🇩", "🇮🇪", "🇮🇱", "🇮🇲",
                "🇮🇳", "🇮🇴", "🇮🇶", "🇮🇷", "🇮🇸", "🇮🇹", "🇯🇪", "🇯🇲", "🇯🇴", "🇯🇵",
                "🇰🇪", "🇰🇬", "🇰🇭", "🇰🇮", "🇰🇲", "🇰🇳", "🇰🇵", "🇰🇷", "🇰🇼", "🇰🇾",
                "🇰🇿", "🇱🇦", "🇱🇧", "🇱🇨", "🇱🇮", "🇱🇰", "🇱🇷", "🇱🇸", "🇱🇹", "🇱🇺",
                "🇱🇻", "🇱🇾", "🇲🇦", "🇲🇨", "🇲🇩", "🇲🇪", "🇲🇫", "🇲🇬", "🇲🇭", "🇲🇰",
                "🇲🇱", "🇲🇲", "🇲🇳", "🇲🇴", "🇲🇵", "🇲🇶", "🇲🇷", "🇲🇸", "🇲🇹", "🇲🇺",
                "🇲🇻", "🇲🇼", "🇲🇽", "🇲🇾", "🇲🇿", "🇳🇦", "🇳🇨", "🇳🇪", "🇳🇫", "🇳🇬",
                "🇳🇮", "🇳🇱", "🇳🇴", "🇳🇵", "🇳🇷", "🇳🇺", "🇳🇿", "🇴🇲", "🇵🇦", "🇵🇪",
                "🇵🇫", "🇵🇬", "🇵🇭", "🇵🇰", "🇵🇱", "🇵🇲", "🇵🇳", "🇵🇷", "🇵🇸", "🇵🇹",
                "🇵🇼", "🇵🇾", "🇶🇦", "🇷🇪", "🇷🇴", "🇷🇸", "🇷🇺", "🇷🇼", "🇸🇦", "🇸🇧",
                "🇸🇨", "🇸🇩", "🇸🇪", "🇸🇬", "🇸🇭", "🇸🇮", "🇸🇯", "🇸🇰", "🇸🇱", "🇸🇲",
                "🇸🇳", "🇸🇴", "🇸🇷", "🇸🇸", "🇸🇹", "🇸🇻", "🇸🇽", "🇸🇾", "🇸🇿", "🇹🇦",
                "🇹🇨", "🇹🇩", "🇹🇫", "🇹🇬", "🇹🇭", "🇹🇯", "🇹🇰", "🇹🇱", "🇹🇲", "🇹🇳",
                "🇹🇴", "🇹🇷", "🇹🇹", "🇹🇻", "🇹🇼", "🇹🇿", "🇺🇦", "🇺🇬", "🇺🇲", "🇺🇸",
                "🇺🇾", "🇺🇿", "🇻🇦", "🇻🇨", "🇻🇪", "🇻🇬", "🇻🇮", "🇻🇳", "🇻🇺", "🇼🇫",
                "🇼🇸", "🇾🇪", "🇾🇹", "🇿🇦", "🇿🇲", "🇿🇼"
            ))
        ))

        categories = baseCategories
        flatList = categories.flatMap { (name, emojis) ->
            listOf(EmojiListItem.Header(name)) + emojis.map { EmojiListItem.Emoji(it) }
        }

        adapter.notifyDataSetChanged()
    }
    // 取得儲存的最近使用 Emoji
    private fun getRecentEmojis(): List<String> {
        val prefs = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("recent_emojis", "") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split(",")
    }
    private fun updateBackground(view: View, settings: SettingsManager) {
        val path = settings.backgroundImagePath
        val bgColor = settings.backgroundColor

        if (path != null && File(path).exists()) {
            try {
                // 1. 載入圖片
                val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return

                // 2. 建立自定義 Drawable 來模仿你提供的繪製邏輯
                val customDrawable = object : Drawable() {
                    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

                    override fun draw(canvas: android.graphics.Canvas) {
                        // 設定透明度
                        paint.alpha = (settings.backgroundImageAlpha * 255).toInt()

                        val viewWidth = bounds.width().toFloat()
                        val viewHeight = bounds.height().toFloat()
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()

                        // --- 核心邏輯：與你提供的代碼完全一致 ---
                        // 取較大的縮放比以填滿寬高 (CenterCrop 效果)
                        val scale = (viewWidth / bitmapWidth).coerceAtLeast(viewHeight / bitmapHeight)
                        val drawW = bitmapWidth * scale
                        val drawH = bitmapHeight * scale

                        // 水平置中，垂直貼頂 (top = 0f)
                        val left = (viewWidth - drawW) / 2f
                        val top = 0f

                        val dstRect = android.graphics.RectF(left, top, left + drawW, top + drawH)

                        canvas.drawBitmap(bitmap, null, dstRect, paint)
                    }

                    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                }

                // 3. 疊加底色與圖片
                val colorDrawable = android.graphics.drawable.ColorDrawable(bgColor)
                val layers = arrayOf(colorDrawable, customDrawable)
                view.background = android.graphics.drawable.LayerDrawable(layers)

            } catch (_: Exception) {
                view.setBackgroundColor(bgColor)
            }
        } else {
            view.setBackgroundColor(bgColor)
        }
    }
    fun updateTargetHeight(heightPx: Int) {
        targetHeightPx = heightPx
        val params = contentContainer.layoutParams
        params.height = heightPx
        contentContainer.layoutParams = params
        requestLayout()
    }

    fun scrollToTop() {
        refreshEmojiData() // 每次開啟或置頂時重新整理列表
        recyclerView.scrollToPosition(0)
        bottomBar.scrollTo(0, 0)
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

    private inner class EmojiAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
                    setTextColor(textColor)
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
                holder.tv.setOnClickListener {
                    onEmojiSelected?.invoke(item.char)
                    settings.addRecentEmoji(context,item.char)
                }
            }
        }
        override fun getItemCount(): Int = flatList.size
        inner class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
        inner class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    }
}