package com.example.tradex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.tradex.service.ScoreParserService;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ScoreParserServiceTest {

    private final ScoreParserService scoreParserService = new ScoreParserService();

    @Test
    @DisplayName("測試 1：數字文字轉換邏輯 (Number Word Parsing)")
    void testParseNumberWords() {
        // 測試基本轉換
        assertEquals("3.6", scoreParserService.parse("three point six"));
        
        // 測試兩位小數 (three point six six)
        assertEquals("3.66", scoreParserService.parse("three point six six"));
        
        // 測試十位數與連字號 (thirty point twenty-five)
        assertEquals("3.25", scoreParserService.parse("three point twenty-five"));
        
        // 測試大小寫不敏感
        assertEquals("4.15", scoreParserService.parse("FOUR POINT FIFTEEN"));
    }

    @Test
    @DisplayName("測試 2：資料清洗與去重邏輯 (Data Cleaning & Deduplication)")
    void testProcessData() {
        List<Map<String, Object>> mockData = new ArrayList<>();
        
        // 模擬髒資料：同一個國家有多筆記錄
        mockData.add(createRow("Singapore", "Asia", "4.3"));
        mockData.add(createRow("SINGAPORE", "Asia", "3.0")); // 低分
        mockData.add(createRow("Vietnam", "Asia", "3.27"));
        mockData.add(createRow("Viet Nam", "Asia", "3.25")); // 測試空格去重
        
        // 執行清洗，限制取前 5 名
        List<Map<String, Object>> result = scoreParserService.processData(mockData, 5);

        // --- 修改 1：驗證去重 (使用更強健的判斷) ---
        long singaporeCount = result.stream()
                .filter(m -> m.get("country").toString().replaceAll("\\s+", "").equalsIgnoreCase("Singapore"))
                .count();
        assertEquals(1, singaporeCount, "新加坡應該被去重合併為一筆");

        long vietnamCount = result.stream()
                .filter(m -> m.get("country").toString().replaceAll("\\s+", "").equalsIgnoreCase("Vietnam"))
                .count();
        assertEquals(1, vietnamCount, "Vietnam 與 Viet Nam 應該被去重合併為一筆");

        // --- 修改 2：驗證正確性 (注意數值轉換) ---
        Map<String, Object> singapore = result.stream()
                .filter(m -> m.get("country").toString().replaceAll("\\s+", "").equalsIgnoreCase("Singapore"))
                .findFirst().get();
        
        // 使用 Double.valueOf 確保比對的是數值而非物件指標
        assertEquals(4.3, Double.valueOf(singapore.get("lpi_score").toString()), 0.001, "應該保留最高分 4.3");
        
        // --- 修改 3：驗證排序 ---
        // 第一名應該是 Singapore (4.3)，第二名應該是 Vietnam (3.27)
        assertEquals("Singapore", result.get(0).get("country"));
        // 因為 Viet Nam 分數較低被併掉了，所以第二名會是 Vietnam
        assertTrue(result.get(1).get("country").toString().contains("Vietnam"), "第二名應該是 Vietnam 系列");
    }

    private Map<String, Object> createRow(String country, String region, String score) {
        Map<String, Object> row = new HashMap<>();
        row.put("country", country);
        row.put("region", region);
        row.put("lpi_score", score);
        return row;
    }
}