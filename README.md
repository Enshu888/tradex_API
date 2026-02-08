# TradeXchange AI Assessment Solution

> 自然語言查詢物流績效指數 (LPI) 數據分析系統

[![Tests](https://img.shields.io/badge/Tests-Passing-success.svg)](./src/test/java)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## ✅ 作業要求完成度

| 必要查詢 | 狀態 | 支持中英文
|---------|------|
| 1. "Which countries in Asia have an LPI score above 3.0?" | ✅ |
| 2. "What's the average LPI score by region?" | ✅ |
| 3. "Show me the top 5 countries by logistics performance" | ✅ |

| 評估重點 | 實現方式 |
|---------|---------|
| **Correctness** | 三個查詢正常運作 + 單元測試驗證 |
| **Error Handling** | API 失敗處理 + 髒資料過濾 + 錯誤訊息 |
| **Code Clarity** | 清晰架構 + 詳細註解 + 模組化設計 |

---

## 🚀 快速開始

### 1. 設定環境變數
建立 `.env` 檔案： 
```properties
OPENROUTER_API_KEY=104
application.properties : supabase.anon.key=your_Anon_key
supabase.url=your_url
```

### 2. 執行測試
```bash
mvn test
```

### 3. 啟動應用
```bash
mvn spring-boot:run
```

### 4. 開啟網頁
```
http://localhost:8080
```

---

## 💡 核心特色

### 資料品質處理
題目提到資料有品質問題，本專案實作：
- **去重邏輯**：同一國家多筆記錄時，保留最高分
- **大小寫統一**：避免 "Singapore" 與 "SINGAPORE" 重複
- **文字轉數字**：處理 "three point six" → "3.6"

### 技術架構
```
Controller (API 端點)
    ↓
AiService (LLM 轉 SQL)
    ↓
ScoreParserService (資料清洗)
    ↓
Supabase (資料庫)
```

---

## 🧪 測試驗證

```bash
$ mvn test

[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**測試覆蓋**：
- ✅ 文字轉數字（"three point six" → "3.6"）
- ✅ 去重邏輯（Singapore 4.3 vs SINGAPORE 3.0 → 保留 4.3）
- ✅ 排序正確性（降序排列）

---

## 📂 專案結構

```
src/
├── main/java/com/example/tradex/
│   ├── controller/TradeController.java        # API 端點
│   ├── service/AiService.java                 # LLM 整合
│   └── service/ScoreParserService.java        # 資料清洗 ⭐
├── main/resources/
│   └── static/index.html                      # 前端介面
└── test/java/com/example/tradex/
    └── ScoreParserServiceTest.java            # 單元測試 ⭐
```

---

## 💻 使用範例

### 查詢 1：亞洲國家 LPI > 3.0
```
Which countries in Asia have an LPI score above 3.0?
```
→ 顯示亞洲地區所有 LPI > 3.0 的國家（已去重）

### 查詢 2：區域平均分數
```
What's the average LPI score by region?
```
→ 顯示各區域平均分數表格

### 查詢 3：前五名國家
```
Show me the top 5 countries by logistics performance
```
→ 顯示前 5 名國家（依分數降序）

---

## 🛠️ 技術棧

- **Backend**：Java 21 + Spring Boot 3.x
- **LLM**：OpenRouter + Google Gemini 2.0 Flash
- **Database**：Supabase REST API
- **Frontend**：HTML + Tailwind CSS
- **Testing**：JUnit 5

---

## 📊 資料品質處理範例

**問題**：資料庫中同一國家有多筆記錄
```
Singapore, Asia, 4.3
SINGAPORE, Asia, 3.0    ← 大小寫不同
singapore, Asia, 2.8    ← 小寫
```

**解決**：
```java
// 統一大寫比對 + 保留最高分
Singapore, Asia, 4.3    ← 只保留這筆
```

**感謝審閱！** 🙏
