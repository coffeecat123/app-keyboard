package com.coffeecat.keyboard.view

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.coffeecat.keyboard.data.BookmarkManager
import com.coffeecat.keyboard.data.SettingsManager
import java.io.File
import java.io.FileOutputStream

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class ClipboardView(context: Context) : LinearLayout(context) {
    private val settings = SettingsManager(context)

    // 定義顏色變數以便後續使用
    private val bgColor = settings.backgroundColor
    private val textColor = settings.textColor
    private val toolbarColor = settings.toolbarColor
    private val userTypeface = settings.getTypeface()
    private val clipHistory = mutableListOf<ClipItem>()
    private val maxHistory = 50 // 最多存 10 筆
    var toastMessage: ((String) -> Unit)? = null
    var onImageSelected: ((Uri, String) -> Unit)? = null
    var onClipSelected: ((String) -> Unit)? = null
    var onBackPressed: (() -> Unit)? = null
    var onAddBookmark: ((String) -> Unit)? = null
    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val title: TextView
    private val recyclerView: RecyclerView
    private val contentContainer: LinearLayout
    private var clipAdapter: ClipAdapter? = null
    private var targetHeightPx: Int = 0
    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    private data class ClipItem(
        val text: String? = null,        // 如果是文字，存在這裡
        val imageUri: Uri? = null, // 如果是圖片，儲存 URI
        val originalUriString: String? = null,      // 紀錄原始路徑 (用來比對重複)
        val mimeType: String = "text/plain",   // 用於告知目標 App 檔案格式 (e.g., "image/png")
        var isExpanded: Boolean = false,
        var isBookmarked: Boolean = false
    ) {
        val isImage: Boolean get() = imageUri != null
    }
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

        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = userTypeface
            includeFontPadding = false
            setTextColor(textColor)
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
        clipAdapter = ClipAdapter(emptyList()) { text ->
            onClipSelected?.invoke(text)
        }
        recyclerView.adapter = clipAdapter
        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition

                if (position != RecyclerView.NO_POSITION) {
                    clipAdapter?.removeItem(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
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
    @SuppressLint("NotifyDataSetChanged")
    fun refreshClipboardData() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val primaryClip = clipboard.primaryClip ?: return

        if (primaryClip.itemCount > 0) {
            val item = primaryClip.getItemAt(0)
            val uri = item.uri
            val text = item.text?.toString()

            when {
                // 圖片處理邏輯
                uri != null -> {
                    val uriString = uri.toString()
                    // 檢查原始路徑是否已存在
                    val existingIndex = clipHistory.indexOfFirst { it.originalUriString == uriString }

                    if (existingIndex != 0) { // 不在第一筆才處理
                        if (existingIndex > 0) {
                            // 已存在：移到最前
                            val existingItem = clipHistory.removeAt(existingIndex)
                            clipHistory.add(0, existingItem)
                        } else {
                            // 全新圖片：轉存
                            val localUri = saveImageToInternalStorage(uri)
                            if (localUri != null) {
                                val type = clipboard.primaryClipDescription?.getMimeType(0) ?: "image/png"
                                clipHistory.add(0, ClipItem(
                                    imageUri = localUri,
                                    originalUriString = uriString,
                                    mimeType = type
                                ))
                            }
                        }
                    }
                }
                // 文字處理邏輯
                !text.isNullOrEmpty() -> {
                    val existingIndex = clipHistory.indexOfFirst { it.text == text }
                    if (existingIndex != 0) {
                        if (existingIndex > 0) {
                            val existingItem = clipHistory.removeAt(existingIndex)
                            clipHistory.add(0, existingItem)
                        } else {
                            clipHistory.add(0, ClipItem(text = text))
                        }
                    }
                }
            }
        }

        // 限制數量並刪除檔案
        while (clipHistory.size > maxHistory) {
            val removed = clipHistory.removeAt(clipHistory.size - 1)
            if (removed.isImage) {
                removed.imageUri?.path?.let { File(it).delete() }
            }
        }

        // 更新書籤狀態
        clipHistory.forEach { it.text?.let { t -> it.isBookmarked = BookmarkManager(context).isBookmarked(t) } }

        clipAdapter?.items = clipHistory.toList()
        setTitle(clipHistory.size)
        clipAdapter?.notifyDataSetChanged()
    }
    private fun setTitle(size:Int){
        title.text="Clipboard (${size})"
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
        refreshClipboardData()
        clipHistory.forEach { it.isExpanded = false }

        // 2. 通知 Adapter 資料已更改，這樣 UI 才會刷回折疊狀態
        clipAdapter?.items = clipHistory.toList()
        clipAdapter?.notifyDataSetChanged()
        recyclerView.scrollToPosition(0)
    }
    fun updateTargetHeight(heightPx: Int) {
        targetHeightPx = heightPx
        val params = contentContainer.layoutParams
        params.height = heightPx
        contentContainer.layoutParams = params
        requestLayout()
    }
    private fun saveImageToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            // 建立一個唯一檔名
            val fileName = "clip_${System.currentTimeMillis()}.png"
            val outFile = File(context.cacheDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private inner class ClipAdapter(
        var items: List<ClipItem>,
        val onClipSelected: (String) -> Unit
    ) : RecyclerView.Adapter<ClipAdapter.ClipVH>() {

        inner class ClipVH(
            root: LinearLayout,
            val tvContent: TextView,
            val ivImage: ImageView,
            val btnExpand: ImageButton,
            val expandArea: View,
            val btnAdd: ImageButton // 新增：Add 按鈕
        ) : RecyclerView.ViewHolder(root)
        fun removeItem(position: Int) {
            if (position in items.indices) {
                val item = items[position]
                // 如果是圖片，刪除實體檔案避免佔空間
                if (item.isImage) {
                    item.imageUri?.path?.let { File(it).delete() }
                }

                clipHistory.removeAt(position)
                this.items = clipHistory.toList()
                setTitle(clipHistory.size)
                notifyItemRemoved(position)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipVH {
            val container = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                }
                val shape = GradientDrawable().apply {
                    setColor(ColorUtils.setAlphaComponent(textColor, 15))
                    cornerRadius = dpToPx(12).toFloat()
                }
                background = shape
            }

            val textView = TextView(parent.context).apply {
                setTextColor(textColor)
                typeface = userTypeface
                textSize = 15f
                setPadding(dpToPx(12), dpToPx(12), dpToPx(8), dpToPx(12))
                includeFontPadding = false
                setLineSpacing(0f, 1.1f)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                background = getSelectableItemBackgroundResource(false)
                isClickable = true
            }
            val imageView = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                adjustViewBounds = true
                setPadding(dpToPx(12), dpToPx(12), dpToPx(8), dpToPx(12))
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                visibility = GONE // 預設隱藏
                background = getSelectableItemBackgroundResource(false)
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
                setColorFilter(textColor)
                background = null
                isClickable = false // 點擊穿透給 Wrapper
                layoutParams = LayoutParams(dpToPx(24), dpToPx(24)).apply {
                    topMargin = dpToPx(10)
                }
            }

            // 2. 新增按鈕 (+)
            val addBtn = ImageButton(parent.context).apply {
                setImageResource(com.coffeecat.keyboard.R.drawable.rounded_add_24) // 請確保有這個 icon 或改用其他名稱
                setColorFilter(textColor)
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
            container.addView(imageView)
            container.addView(expandWrapper)

            return ClipVH(container, textView, imageView, expandBtn, expandWrapper, addBtn)
        }

        override fun onBindViewHolder(holder: ClipVH, position: Int) {
            val item = items[position]

            if (item.isImage) {
                // --- 處理圖片模式 ---
                holder.tvContent.visibility = GONE
                holder.ivImage.visibility = VISIBLE
                holder.btnAdd.visibility = GONE // 圖片通常不支援「加入書籤」功能
                holder.btnExpand.visibility = VISIBLE // 圖片通常不需要展開

                // 顯示圖片預覽
                holder.ivImage.setImageURI(item.imageUri)
// 設定圖片高度控制
                val imgParams = holder.ivImage.layoutParams
                if (item.isExpanded) {
                    // 展開：顯示完整高度
                    imgParams.height = LayoutParams.MATCH_PARENT
                    holder.btnExpand.rotation = 180f
                    holder.ivImage.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    // 折疊：限制在一個固定高度（例如 120dp）
                    imgParams.height = dpToPx(60)
                    holder.btnExpand.rotation = 0f
                    // 使用 CENTER_CROP 讓縮圖比較美觀
                    holder.ivImage.scaleType = ImageView.ScaleType.CENTER_CROP
                }
                holder.ivImage.layoutParams = imgParams
                // 點擊圖片：執行發送圖片的回調
                holder.ivImage.setOnClickListener {
                    item.imageUri?.let { uri -> onImageSelected?.invoke(uri, item.mimeType) }
                }

            } else {
                // --- 處理文字模式 (你原本的邏輯) ---
                holder.tvContent.visibility = VISIBLE
                holder.ivImage.visibility = GONE
                holder.btnAdd.visibility = VISIBLE
                holder.btnExpand.visibility = VISIBLE

                holder.tvContent.text = item.text
                holder.tvContent.setOnClickListener {
                    item.text?.let { text -> onClipSelected.invoke(text) }
                }

                // 書籤按鈕邏輯
                if (item.isBookmarked) {
                    holder.btnAdd.setImageResource(com.coffeecat.keyboard.R.drawable.rounded_check_24)
                    holder.btnAdd.isEnabled = false
                    holder.btnAdd.alpha = 0.5f
                } else {
                    holder.btnAdd.setImageResource(com.coffeecat.keyboard.R.drawable.rounded_add_24)
                    holder.btnAdd.isEnabled = true
                    holder.btnAdd.alpha = 1.0f
                    holder.btnAdd.setOnClickListener {
                        item.text?.let { text ->
                            item.isBookmarked = true
                            onAddBookmark?.invoke(text)
                            notifyItemChanged(holder.adapterPosition)
                        }
                    }
                }

                // 處理文字展開/收縮邏輯
                val params = holder.tvContent.layoutParams
                if (item.isExpanded) {
                    holder.tvContent.maxLines = Int.MAX_VALUE
                    holder.btnExpand.rotation = 180f
                    params.height = LayoutParams.MATCH_PARENT
                } else {
                    holder.tvContent.maxLines = 2
                    holder.btnExpand.rotation = 0f
                    val lineHeight = holder.tvContent.lineHeight
                    params.height = dpToPx(24) + (lineHeight * 1.5).toInt()
                }
                holder.tvContent.layoutParams = params
            }

            // 右側展開按鈕區域點擊
            holder.expandArea.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    // 不再檢查 !items[currentPos].isImage，讓圖片也能切換 isExpanded
                    items[currentPos].isExpanded = !items[currentPos].isExpanded
                    notifyItemChanged(currentPos)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}