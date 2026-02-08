package com.example.tradex.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiService {
    
    @Value("${openrouter.api.key}")
    private String apiKey;
    private final String API_URL = "https://openrouter.ai/api/v1/chat/completions";

    public String translateToSupabasePath(String userQuestion) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 設定 Headers
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 2. 建立 System Prompt
        String systemPrompt = """
                You are a Supabase API expert. 
                Table: countries_lpi (id, country, region, lpi_score, year)
                
                Task: Convert user questions into Supabase REST API query parameters.
                Rules:
                1. For "above 3.0", use `lpi_score=gt.3.0`.
                2. For "Asia", use `region=ilike.*Asia*`.
                3. For "top 5", use `order=lpi_score.desc&limit=50`.
                4. For "average by region", since REST API doesn't support easy AVG, return '/rest/v1/countries_lpi?select=region,lpi_score'. 
                5. IMPORTANT: Output ONLY the path starting with /rest/v1/...
                """;
        // 3. 組合 Request Body (簡單 JSON 結構)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "google/gemini-2.0-flash-001"); // 快又省
        requestBody.put("messages", List.of(
        Map.of("role", "system", "content", systemPrompt),
        Map.of("role", "user", "content", userQuestion)
        ));

        // 4. 發送請求
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(API_URL, entity, Map.class);
            
            // 解析 AI 回傳的 SQL (這部分根據 API 結構抓取 content)
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return message.get("content").toString().trim();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}