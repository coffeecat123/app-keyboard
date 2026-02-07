package com.coffeecat.keyboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color as AndroidColor
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.coffeecat.keyboard.data.SettingsManager
import com.coffeecat.keyboard.view.KeyboardView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsManager(this)
        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val scrollState = rememberScrollState() // 讓整個頁面可滾動
            var refreshTrigger by remember { mutableIntStateOf(0) }
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as ComponentActivity).window
                    // 設定狀態欄顏色為主題的背景色或 surface
                    window.statusBarColor = colorScheme.surface.toArgb()

                    // 設定導覽列顏色 (選配)
                    window.navigationBarColor = colorScheme.surface.toArgb()

                    // 控制狀態欄圖示顏色 (深色模式時使用淺色圖示，反之亦然)
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                    // 控制導覽列圖示顏色
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 主容器：使用 verticalScroll 避免小螢幕顯示不全
                        Column(
                            modifier = Modifier.weight(1f)
                                .padding(horizontal = 20.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Text("Keyboard Settings", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(20.dp))

                            // --- 步驟區 ---
                            Button(
                                onClick = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("1. 啟用鍵盤") }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.showInputMethodPicker()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("2. 選取鍵盤") }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                            // --- 插入你的外觀設定區 ---
                            KeyboardSettingsScreen(context, settings) {
                                refreshTrigger++
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(24.dp))

                            // 底部留白防止被導覽列擋住
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // 傳入觸發器
                                KeyboardPreview(settings, refreshTrigger)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun KeyboardPreview(settings: SettingsManager, refreshTrigger: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val keyboardView = remember {
        KeyboardView(context).apply { applyStyles(settings) }
    }

    AndroidView(
        factory = { keyboardView },
        modifier = Modifier.fillMaxWidth()
            .height(with(density) { keyboardView.getMeasuredKeyboardHeightPx().toDp() }),
        update = { view ->
            val _a = refreshTrigger

            // 現在這裡會被觸發
            view.applyStyles(settings)
        }
    )
}
@Composable
fun KeyboardSettingsScreen(
    context: Context,
    settings: SettingsManager,
    onChanged: () -> Unit // 加入回呼函數
) {
    val settings = remember { SettingsManager(context) }

    // 使用 State 監聽設定值，確保 UI 畫面會同步更新
    var bgColor by remember { mutableIntStateOf(settings.backgroundColor) }
    var textColor by remember { mutableIntStateOf(settings.textColor) }
    var accentColor by remember { mutableIntStateOf(settings.accentColor) }
    var keyColor by remember { mutableIntStateOf(settings.keyColor) }
    var toolbarColor by remember { mutableIntStateOf(settings.toolbarColor) }
    var functionKeyColor by remember { mutableIntStateOf(settings.functionKeyColor) }
    var functionTextColor by remember { mutableIntStateOf(settings.functionTextColor) }
    var showOutline by remember { mutableStateOf(settings.showOutline) }
    var showButton by remember { mutableStateOf(settings.showButton) }
    var showText by remember { mutableStateOf(settings.showText) }
    var selectedFont by remember { mutableIntStateOf(settings.fontFamily) }
    var backgroundImagePath by remember { mutableStateOf(settings.backgroundImagePath) }
    var bgAlpha by remember { mutableFloatStateOf(settings.backgroundImageAlpha) }
    // 圖片選取器邏輯
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // 將圖片複製到內部空間，避免權限問題
            val inputStream = context.contentResolver.openInputStream(it)
            val file = File(context.filesDir, "keyboard_bg.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output -> input.copyTo(output) }
            }
            // 更新設定與 UI
            settings.backgroundImagePath = file.absolutePath
            backgroundImagePath = file.absolutePath
            onChanged()
        }
    }
    Column {

        Text("Custom Keyboard", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Image bg", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (backgroundImagePath == null) "select" else "change")
                    }
                    if (backgroundImagePath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            settings.backgroundImagePath = null
                            backgroundImagePath = null
                        }) {
                            Text("remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (backgroundImagePath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HSVSlider(label = "A", value = bgAlpha, range = 0f..1f) {
                        bgAlpha = it
                        settings.backgroundImageAlpha = it
                        onChanged() // 讓下方預覽即時變動
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HSVColorPicker(
            enableAlpha = false,
            label = "bgColor",
            initialColor = bgColor,
            onColorSelected = {
                bgColor = it
                settings.backgroundColor = it
                onChanged()
            }

        )

        Spacer(modifier = Modifier.height(16.dp))

        HSVColorPicker(
            label = "textColor",
            initialColor = textColor,
            onColorSelected = {
                textColor = it
                settings.textColor = it
                onChanged()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        HSVColorPicker(
            label = "accentColor",
            initialColor = accentColor,
            onColorSelected = {
                accentColor = it
                settings.accentColor = it
                onChanged()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        HSVColorPicker(
            label = "keyColor",
            initialColor = keyColor,
            onColorSelected = {
                keyColor = it
                settings.keyColor = it
                onChanged()
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        HSVColorPicker(
            label = "toolbarColor",
            initialColor = toolbarColor,
            onColorSelected = {
                toolbarColor = it
                settings.toolbarColor = it
                onChanged()
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        HSVColorPicker(
            label = "functionKeyColor",
            initialColor = functionKeyColor,
            onColorSelected = {
                functionKeyColor = it
                settings.functionKeyColor = it
                onChanged()
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        HSVColorPicker(
            label = "functionTextColor",
            initialColor = functionTextColor,
            onColorSelected = {
                functionTextColor = it
                settings.functionTextColor = it
                onChanged()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        // 3. 字體切換
        Text("字體樣式", style = MaterialTheme.typography.labelLarge)
        val fonts = listOf("預設 (Sans)", "襯線 (Serif)", "等寬 (Mono)")
        fonts.forEachIndexed { index, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    selectedFont = index
                    settings.fontFamily = index
                    onChanged()
                }.fillMaxWidth()
            ) {
                RadioButton(selected = selectedFont == index, onClick = {
                    selectedFont = index
                    settings.fontFamily = index
                    onChanged()
                })
                Text(name)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. 開關
        SettingsSwitch("顯示按鍵背景", showButton) {
            showButton = it
            settings.showButton = it
            onChanged()
        }

        SettingsSwitch("顯示按鍵邊框", showOutline) {
            showOutline = it
            settings.showOutline = it
            onChanged()
        }

        SettingsSwitch("顯示文字", showText) {
            showText = it
            settings.showText = it
            onChanged()
        }
    }
}

@Composable
fun HSVColorPicker(
    enableAlpha: Boolean=true,
    label: String,
    initialColor: Int,
    onColorSelected: (Int) -> Unit
) {
    // 解析初始顏色
    val hsv = remember(initialColor) { FloatArray(3).apply { AndroidColor.colorToHSV(initialColor, this) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(AndroidColor.alpha(initialColor) / 255f) }

    val currentColor = Color(AndroidColor.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value)))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))

                // 預覽區：底層棋盤格 + 上層顏色
                Box(modifier = Modifier.size(40.dp, 24.dp).background(Color.LightGray)) {
                    Text("▒▒", color = Color.White, style = MaterialTheme.typography.bodySmall) // 簡易棋盤格暗示
                    Box(modifier = Modifier.fillMaxSize().background(currentColor))
                }
            }

            // HSV 軌道
            HSVSlider("H", hue, 0f..360f) { hue = it; onColorSelected(AndroidColor.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value))) }
            HSVSlider("S", saturation, 0f..1f) { saturation = it; onColorSelected(AndroidColor.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value))) }
            HSVSlider("V", value, 0f..1f) { value = it; onColorSelected(AndroidColor.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value))) }
            if(enableAlpha)HSVSlider("A", alpha, 0f..1f) { alpha = it; onColorSelected(AndroidColor.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value))) }

        }
    }
}

@Composable
fun HSVSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
        Text(label, modifier = Modifier.width(32.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}