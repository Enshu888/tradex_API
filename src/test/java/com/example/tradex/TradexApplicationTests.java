package com.example.tradex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "OPENROUTER_API_KEY=mock_key_for_test",
    "supabase.anon.key=mock_key",
    "supabase.url=http://localhost:8080"
})
@ActiveProfiles("test")
class TradexApplicationTests {
    @Test
    void contextLoads() {
    }
}
