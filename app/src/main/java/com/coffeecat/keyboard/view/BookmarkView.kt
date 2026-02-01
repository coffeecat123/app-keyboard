package com.coffeecat.keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.coffeecat.keyboard.data.BookmarkManager
import com.google.android.material.R

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class BookmarkView(context: Context) : LinearLayout(context) {
    private val bookmarkManager = BookmarkManager(context)

    var toastMessage: ((String) -> Unit)? = null
    var onBookmarkSelected: ((String) -> Unit)? = null
    var onBackPressed: (() -> Unit)? = null
    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val title: TextView
    private val recyclerView: RecyclerView
    private val contentContainer: LinearLayout
    private var bookmarkAdapter: BookmarkAdapter? = null
    private var targetHeightPx: Int = 0
    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
    private data class BookmarkItem(
        val content: String,
        var isExpanded: Boolean = false
    )
    init {
        orientation = VERTICAL

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)
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

        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
            setTextColor(getThemeColor(R.attr.colorOnSurface))
            setPadding(dpToPx(12), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        setTitle(0)

        toolbar.addView(backBtn)
        toolbar.addView(title)
        contentContainer.addView(toolbar)

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            itemAnimator = null
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        contentContainer.addView(recyclerView)
        this.isFocusable = true
        this.isFocusableInTouchMode = true
        recyclerView.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
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
        bookmarkAdapter = BookmarkAdapter(emptyList()) { text ->
            this@BookmarkView.onBookmarkSelected?.invoke(text)
        }
        recyclerView.adapter = bookmarkAdapter
    }
    @SuppressLint("NotifyDataSetChanged")
    fun refreshBookmarkData() {
        val bookmarks = bookmarkManager.getBookmarks() // 從 Manager 讀取真實資料
        setTitle(bookmarks.size)
        bookmarkAdapter?.items = bookmarks.map { BookmarkItem(it) }
        bookmarkAdapter?.notifyDataSetChanged()
    }
    private fun setTitle(size:Int){
        title.text="Bookmark (${size})"
    }
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
    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
    }
    fun updateTargetHeight(heightPx: Int) {
        targetHeightPx = heightPx
        val params = contentContainer.layoutParams
        params.height = heightPx
        contentContainer.layoutParams = params
        requestLayout()
    }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private inner class BookmarkAdapter(
        var items: List<BookmarkItem>,
        val onBookmarkSelected: (String) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.BookmarkVH>() {

        inner class BookmarkVH(
            root: LinearLayout,
            val tvContent: TextView,
            val btnExpand: ImageButton,
            val expandArea: View,
            val btnDelete: ImageButton
        ) : RecyclerView.ViewHolder(root)
        fun removeItem(position: Int) {
            if (position in items.indices) {
                val removedItem = items[position] // 取得要刪除的內容
                val mutableList = items.toMutableList()
                mutableList.removeAt(position)
                items = mutableList

                // --- 核心修正：同步到資料庫/SharedPreferences ---
                bookmarkManager.removeBookmark(removedItem.content)
                // -------------------------------------------

                setTitle(items.size)
                notifyItemRemoved(position)

                if (position < items.size) {
                    notifyItemRangeChanged(position, items.size - position)
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkVH {
            val container = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                }
                val shape = GradientDrawable().apply {
                    setColor(ColorUtils.setAlphaComponent(getThemeColor(R.attr.colorOnSurface), 15))
                    cornerRadius = dpToPx(12).toFloat()
                }
                background = shape
            }

            val textView = TextView(parent.context).apply {
                setTextColor(getThemeColor(R.attr.colorOnSurface))
                textSize = 15f
                setPadding(dpToPx(12), dpToPx(12), dpToPx(8), dpToPx(12))
                includeFontPadding = false
                setLineSpacing(0f, 1.1f)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                background = getSelectableItemBackgroundResource(false)
                isClickable = true
            }

            // 右側區域容器：改為垂直 LinearLayout
            val expandWrapper = LinearLayout(parent.context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LayoutParams(dpToPx(56), LayoutParams.MATCH_PARENT)
                background = getSelectableItemBackgroundResource(false)
                isClickable = true
            }

            // 1. 展開箭頭
            val expandBtn = ImageButton(parent.context).apply {
                setImageResource(com.coffeecat.keyboard.R.drawable.rounded_keyboard_arrow_down_24)
                setColorFilter(getThemeColor(R.attr.colorOnSurface))
                background = null
                isClickable = false // 點擊穿透給 Wrapper
                layoutParams = LayoutParams(dpToPx(24), dpToPx(24)).apply {
                    topMargin = dpToPx(10)
                }
            }

            // 2. 新增按鈕 (+)
            val addBtn = ImageButton(parent.context).apply {
                setImageResource(com.coffeecat.keyboard.R.drawable.rounded_delete_forever_24) // 請確保有這個 icon 或改用其他名稱
                setColorFilter(getThemeColor(R.attr.colorOnSurface))
                background = getSelectableItemBackgroundResource(true) // 獨立的水波紋
                isClickable = true // 獨立點擊，不穿透
                layoutParams = LayoutParams(dpToPx(32), dpToPx(32)).apply {
                    topMargin = dpToPx(8)
                    bottomMargin = dpToPx(8)
                }
            }

            expandWrapper.addView(expandBtn)
            expandWrapper.addView(addBtn)

            container.addView(textView)
            container.addView(expandWrapper)

            return BookmarkVH(container, textView, expandBtn, expandWrapper, addBtn)
        }

        override fun onBindViewHolder(holder: BookmarkVH, position: Int) {
            val item = items[position]
            holder.tvContent.text = item.content
            holder.tvContent.setOnClickListener { onBookmarkSelected(item.content) }
            val params = holder.tvContent.layoutParams
            // 文字區點擊：選取剪貼簿文字
            holder.tvContent.setOnClickListener { onBookmarkSelected(item.content) }
            holder.btnDelete.setOnClickListener {
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                removeItem(holder.adapterPosition)
            }
            if (item.isExpanded) {
                // --- 展開狀態 ---
                holder.tvContent.maxLines = Int.MAX_VALUE
                holder.tvContent.ellipsize = null
                holder.btnExpand.rotation = 180f

                // 高度恢復為自動包裹內容
                params.height = LayoutParams.MATCH_PARENT
            } else {
                // --- 收縮狀態 (1.5行效果) ---
                holder.tvContent.maxLines = 2
                holder.tvContent.ellipsize = null
                holder.btnExpand.rotation = 0f

                val lineHeight = holder.tvContent.lineHeight
                params.height = dpToPx(24) + (lineHeight * 1.5).toInt()
            }
            holder.tvContent.layoutParams = params

            // 右側整塊區域點擊：切換展開狀態
            holder.expandArea.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val currentItem = items[currentPos]
                    currentItem.isExpanded = !currentItem.isExpanded
                    notifyItemChanged(currentPos)

                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}