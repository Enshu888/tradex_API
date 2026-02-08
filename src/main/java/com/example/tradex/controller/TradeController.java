package com.example.tradex.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.tradex.service.AiService;
import com.example.tradex.service.ScoreParserService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TradeController {

    // 從 application.properties 注入設定
    @Value("${supabase.anon.key}")
    private String supabaseKey;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Autowired
    private AiService aiService;

    @Autowired
    private ScoreParserService scoreParserService;

    /**
     * 讀取設定檔
     */
    @GetMapping("/test-data")
    public String testSupabase() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();

        headers.set("apikey", supabaseKey);
        headers.set("Authorization", "Bearer " + supabaseKey);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = supabaseUrl + "/rest/v1/countries_lpi?select=*&limit=50";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    /**
     * AI 智能問答接口
     */
    @GetMapping("/ask")
    public Object ask(@RequestParam String question) {

        try {
            // --- 2. 決定 Supabase 查詢路徑 ---
            String apiPath = aiService.translateToSupabasePath(question);
            if (question.contains("前五") || question.toLowerCase().contains("top 5")) {
                // 強制抓取核心欄位，由 Java 進行後處理以確保去重與排序穩定
                apiPath = "/rest/v1/countries_lpi?select=country,region,lpi_score";
            } else {
                apiPath = aiService.translateToSupabasePath(question);
            }

            // 檢查 AI 是否回傳了無效內容 (非業務相關)
            if (apiPath == null || !apiPath.contains("countries_lpi")) {
            // 改為回傳一個明確的 JSON 物件，讓前端知道這是「文字訊息」
            return Map.of("type", "text", "content", "我目前只能處理 LPI 物流數據相關的問題，請試著問我關於國家分數或區域平均！");
            }

            // --- 3. 呼叫 Supabase ---
            String fullUrl = supabaseUrl + apiPath;
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseKey);
            headers.set("Authorization", "Bearer " + supabaseKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> data = response.getBody();

            if (data == null || data.isEmpty()) return List.of();

            // --- 4. 針對「平均分數」問題做後處理 ---
            if (question.contains("average") || question.contains("平均")) {
                return calculateAverage(data);
            }

            // --- 5. 針對「前五 / top 5」這類問題 ---
            if (question.contains("前五") || question.toLowerCase().contains("top 5")) {
                int limit = 5;
                return scoreParserService.processData(data, limit);
            }

            // 6. 針對一般查詢問題，增加一個「嚴格大於」的過濾與「去重」邏輯
            if (question.contains("above") || question.contains("高於") || question.contains("大於")) {
                double threshold = 0;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(\\.\\d+)?").matcher(question);
            if (m.find()) {
                    threshold = Double.parseDouble(m.group());
                }

            final double finalThreshold = threshold;

            // --- 核心修復：先過濾，再透過 Map 去重 ---
            return data.stream()
                .filter(row -> {
                    String scoreStr = scoreParserService.parse(row.get("lpi_score"));
                    if (scoreStr != null && scoreStr.matches("^[0-9.]+$")) {
                        return Double.parseDouble(scoreStr) > finalThreshold;
                    }
                    return false;
                })
                .collect(Collectors.toMap(
                    row -> row.get("country").toString().replaceAll("\\s+", "").toUpperCase(), // 以大寫國家名為 Key (去重關鍵)
                    row -> row,
                    (existing, replacement) -> {
                        // 如果遇到重複，保留分數較高的那筆
                        double scoreExisting = Double.parseDouble(scoreParserService.parse(existing.get("lpi_score")));
                        double scoreReplacement = Double.parseDouble(scoreParserService.parse(replacement.get("lpi_score")));
                        return scoreExisting >= scoreReplacement ? existing : replacement;
                    }
                ))
                .values().stream()
                .sorted((a, b) -> { // 補上排序，確保表格從高分排到低分
                    double sA = Double.parseDouble(scoreParserService.parse(a.get("lpi_score")));
                    double sB = Double.parseDouble(scoreParserService.parse(b.get("lpi_score")));
                    return Double.compare(sB, sA);
                })
                .collect(Collectors.toList());
    }

            return data;

        } catch (Exception e) {
            System.err.println("系統執行錯誤: " + e.getMessage());
            return Map.of("error", "數據解析發生異常，請稍後再試。");
        }
    }

    /**
     * 計算區域平均值 (處理大小寫與髒資料)
     */
    private Map<String, Double> calculateAverage(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) return Map.of();

        return data.stream()
                .filter(row -> row.get("region") != null && row.get("lpi_score") != null)
                .map(row -> {
                    String cleanedScore = scoreParserService.parse(row.get("lpi_score"));
                    String normalizedRegion = row.get("region").toString().toUpperCase().trim();

                    java.util.Map<String, Object> normalizedRow = new java.util.HashMap<>();
                    normalizedRow.put("region", normalizedRegion);
                    normalizedRow.put("lpi_score", cleanedScore);
                    return normalizedRow;
                })
                .filter(row -> row.get("lpi_score").toString().matches("^[0-9.]+$"))
                .collect(Collectors.groupingBy(
                        row -> row.get("region").toString(),
                        Collectors.averagingDouble(
                                row -> Double.parseDouble(row.get("lpi_score").toString())
                        )
                ));
    }
}