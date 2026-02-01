# CoffeeCat Keyboard ☕🐾

[![GitHub tag](https://img.shields.io/github/v/tag/coffeecat123/app-keyboard?label=Latest%20Release)](https://github.com/coffeecat123/app-keyboard/releases)

## 🐈 專案介紹 (Project Overview)

一個基於 Kotlin 開發的 Android 自定義輸入法 (IME)，結合了注音、英文輸入與強大的生產力工具（剪貼簿、常用語、Emoji）。本專案旨在提供一個輕量、流暢且具備高度擴充性的輸入體驗。

## ✨ 主要功能

* **多模式輸入**：支援 **注音 (Bopomofo)** 與 **英文 (QWERTY)** 鍵盤，內建候選字導航。
* **智能剪貼簿**：`ClipboardView` 紀錄近期複製內容，支援文字與圖片的快速貼上，並可直接將剪貼項轉存為常用語。
* **常用語收藏 (Bookmarks)**：透過 `BookmarkManager` 管理常用文字，點擊即可輸入，適合儲存地址、帳號或常用回覆。
* **Emoji 選擇器**：分類完整的 Emoji 面板，支援快速滾動切換。
* **文字編輯模式 (EDIT)**：專為精細操作設計的佈局，提供方向鍵、選取、全選、複製與貼上功能。
* **Material You 支援**：完美適配 Android 12+ 的動態配色系統，並支援深色模式。

## 🛠️ 技術細節

* **自定義繪製**：`KeyboardView` 使用 `Canvas` 進行原生繪製，非 XML 堆疊，效能更佳。
* **非同步載入**：優化 `Bookmark` 與 `Emoji` 的加載流程，確保鍵盤彈出不卡頓。
* **DSL 佈局定義**：使用 Kotlin DSL 結構化定義 `KeyboardLayouts`，方便自定義鍵盤排列。

## 🚀 安裝與啟用

### 1. 下載與安裝
* **正式版本**：前往 [Releases](https://github.com/你的用戶名/你的倉庫名/releases) 頁面下載最新的 APK 檔案進行安裝。
* **自行編譯**：使用 Android Studio 編譯原始碼並安裝到您的設備。

### 2. 啟用步驟
透過內建的 `MainActivity` 指引或手動操作：
1. 前往系統 **設定 > 系統 > 語言與輸入法 > 螢幕鍵盤**。
2. 開啟 **CoffeeCat Keyboard**。
3. 點擊「選取鍵盤」將預設輸入法切換為 **CoffeeCat**。

## 📄 開源授權

本專案採用 **MIT License** 授權。詳情請見 [LICENSE](LICENSE) 檔案。

Copyright (c) [2026] [coffeecat123]


> （註：文件部分內容可能由 AI 產生）