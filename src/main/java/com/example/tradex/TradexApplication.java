package com.example.tradex;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradexApplication {

	public static void main(String[] args) {
		// 1. 載入 .env 檔案
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

		// 2. 將 .env 中的變數注入到 Java 的 System Properties
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
		
		// 3. 啟動 Spring
		SpringApplication.run(TradexApplication.class, args);

	}

}
