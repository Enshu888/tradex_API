package com.example.tradex.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ScoreParserService {

    private static final Map<String, String> NUM_WORD_MAP = new HashMap<>();

    // 靜態初始化：自動生成 0-99 的英文數字對照表
    static {
        String[] units = {
                "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
                "seventeen", "eighteen", "nineteen"
        };
        String[] tens = {
                "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        };

        // 填充 0-19
        for (int i = 0; i < 20; i++) {
            NUM_WORD_MAP.put(units[i], String.valueOf(i));
        }
        // 填充 20-99 (自動組合，如 thirty-two 或 thirty two)
        for (int i = 2; i < 10; i++) {
            NUM_WORD_MAP.put(tens[i], String.valueOf(i * 10));
            for (int j = 1; j < 10; j++) {
                NUM_WORD_MAP.put(tens[i] + " " + units[j], String.valueOf(i * 10 + j));
                NUM_WORD_MAP.put(tens[i] + "-" + units[j], String.valueOf(i * 10 + j));
            }
        }
    }

    /**
     * 通用解析器：將文字型分數（如 "three point six six"）轉換為數字字串（"3.66"）
     */
    public String parse(Object rawValue) {
        if (rawValue == null) return "";
        String normalized = rawValue.toString().toLowerCase().trim();

        // 檢查是否符合 "xxx point yyy" 格式
        if (normalized.contains(" point ")) {
            String[] sections = normalized.split(" point ");
            if (sections.length == 2) {
                String integerPart = NUM_WORD_MAP.getOrDefault(sections[0], "");
                String decimalPart = parseDecimalPart(sections[1]);

                if (!integerPart.isEmpty() && !decimalPart.isEmpty()) {
                    return integerPart + "." + decimalPart;
                }
            }
        }

        // 若不符合轉換規則，回傳原值，由後續 Filter 處理
        return normalized;
    }

    /**
     * 將 "six six" / "fifteen" 等小數部分轉為數字字串
     */
    private String parseDecimalPart(String decimalSection) {
        if (decimalSection == null || decimalSection.isEmpty()) return "";

        // 優先嘗試整體轉換 (例如 "fifteen")
        if (NUM_WORD_MAP.containsKey(decimalSection)) {
            return NUM_WORD_MAP.get(decimalSection);
        }

        // 否則嘗試逐字轉換 (例如 "six six")
        StringBuilder sb = new StringBuilder();
        for (String word : decimalSection.split(" ")) {
            String n = NUM_WORD_MAP.get(word);
            if (n != null) sb.append(n);
        }
        return sb.toString();
    }

    /**
     * 給「前 N 名」用的資料清洗：
     * 1. 轉換 lpi_score 成數字
     * 2. 以 country 做去重（忽略大小寫）
     * 3. 每個國家只保留最高分那筆
     * 4. 由高到低排序，取前 limit 名
     */
    public List<Map<String, Object>> processData(List<Map<String, Object>> data, Integer limit) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        int finalLimit = (limit == null || limit <= 0) ? Integer.MAX_VALUE : limit;

        // 先整理成 CountryScore，再以國家去重
        Map<String, CountryScore> bestByCountry = data.stream()
                // 1. 過濾：country / lpi_score 不能為 null
                .filter(row -> row.get("country") != null && row.get("lpi_score") != null)
                .map(row -> {
                    String countryRaw = row.get("country").toString().trim();
                    if (countryRaw.isEmpty()) return null;

                    // 修改後：去掉所有空格並轉大寫，這樣 "Viet Nam" 和 "Vietnam" 就會變成同一個 Key ("VIETNAM")
                    String countryKey = countryRaw.replaceAll("\\s+", "").toUpperCase();

                    // 除錯用
                    System.out.println("原始: " + countryRaw + " -> Key: " + countryKey);

                    // three point six -> 3.6
                    String cleanedScore = this.parse(row.get("lpi_score"));
                    if (cleanedScore == null) return null;

                    String scoreStr = cleanedScore.trim();
                    // 只接受數字與小數點
                    if (!scoreStr.matches("^[0-9.]+$")) return null;

                    double score;
                    try {
                        score = Double.parseDouble(scoreStr);
                    } catch (NumberFormatException e) {
                        return null;
                    }

                    Object region = row.get("region");

                    return new CountryScore(countryKey, countryRaw, region, score);
                })
                .filter(Objects::nonNull)
                // 2. 以 countryKey 分組，只保留該國家最高分
                .collect(Collectors.toMap(
                        cs -> cs.countryKey,
                        cs -> cs,
                        (cs1, cs2) -> (cs1.score >= cs2.score) ? cs1 : cs2
                ));

        // 2. 將去重後的結果先按分數由高到低排序
        List<CountryScore> sortedAll = bestByCountry.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());

        if (sortedAll.isEmpty()) return Collections.emptyList();

        // 3. 計算門檻分數 (Threshold Score)
        // 如果 limit 是 5，我們就看清單中第 5 名的分數是多少
        double thresholdScore;
        if (limit != null && limit > 0 && limit <= sortedAll.size()) {
            thresholdScore = sortedAll.get(limit - 1).score;
        } else {
            // 如果不限制數量，或者資料量不足 limit，門檻就是最後一名的分數（或是直接全回傳）
            thresholdScore = sortedAll.get(sortedAll.size() - 1).score;
        }

        // 4. 過濾出所有「大於或等於」門檻分數的國家
        // 這樣如果第 5 名和第 6 名都是 4.04，兩者都會被包含進來
        return sortedAll.stream()
                .filter(cs -> cs.score >= thresholdScore)
                .map(cs -> {
                    Map<String, Object> result = new LinkedHashMap<>(); // 使用 LinkedHashMap 保持順序
                    result.put("country", cs.displayName);
                    result.put("region", cs.region);
                    result.put("lpi_score", cs.score);
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 內部用的小資料結構：代表一個國家的最佳分數
     */
    private static class CountryScore {
        final String countryKey;   // 用來去重（大寫）
        final String displayName;  // 原始國名（顯示用）
        final Object region;
        final double score;

        CountryScore(String countryKey, String displayName, Object region, double score) {
            this.countryKey = countryKey;
            this.displayName = displayName;
            this.region = region;
            this.score = score;
        }
    }
}
