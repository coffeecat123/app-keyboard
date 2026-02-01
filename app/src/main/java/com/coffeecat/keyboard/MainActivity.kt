package com.coffeecat.keyboard

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 使用變數來儲存輸入框的文字內容
            var testText by remember { mutableStateOf("") }


            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()

            // 如果是 Android 12+ (API 31)，使用系統動態顏色，否則使用預設色盤
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("CoffeeCat 設定", style = MaterialTheme.typography.headlineMedium)

                        Spacer(modifier = Modifier.height(20.dp))

                        // 步驟 1
                        Button(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. 啟用鍵盤")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 步驟 2
                        Button(
                            onClick = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("2. 選取鍵盤")
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // 測試區區隔線
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(20.dp))

                        // 測試輸入框
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            label = { Text("在此輸入文字測試鍵盤") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("點擊這裡彈出鍵盤") }
                        )
                    }
                }
            }
        }
    }
}