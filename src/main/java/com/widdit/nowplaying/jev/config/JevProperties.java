package com.widdit.nowplaying.jev.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Cấu hình cho TypeSafe Jev Client.
 * Tự động nhận diện cấu hình từ application.yml, biến môi trường (TYPESAFE_API_KEY / JEV_API_KEY),
 * hoặc JVM System Properties (-Djev.api-key=...).
 */
@Component
@Data
public class JevProperties {

    /**
     * Bật/tắt tính năng gọi Jev.
     */
    @Value("${jev.enabled:true}")
    private boolean enabled = true;

    /**
     * API Key của TypeSafe.
     */
    @Value("${jev.api-key:}")
    @ToString.Exclude
    private String apiKey = "";

    /**
     * Địa chỉ endpoint Jev System One. Mặc định là https://api.typesafe.ai/v1/systemone
     */
    @Value("${jev.api-url:https://api.typesafe.ai/v1/systemone}")
    private String apiUrl = "https://api.typesafe.ai/v1/systemone";

    /**
     * Tên model. Mặc định "jev-latest".
     */
    @Value("${jev.model:jev-latest}")
    private String model = "jev-latest";

    /**
     * Timeout tối đa cho request (mili-giây). Mặc định 2500ms để không làm gián đoạn phát nhạc.
     */
    @Value("${jev.timeout-ms:2500}")
    private int timeoutMs = 2500;

    /**
     * Bật bộ nhớ đệm (In-memory cache) cho các câu trả lời trùng lặp.
     */
    @Value("${jev.cache-enabled:true}")
    private boolean cacheEnabled = true;

    /**
     * Sức chứa tối đa của cache (số lượng bản ghi).
     */
    @Value("${jev.cache-capacity:1000}")
    private int cacheCapacity = 1000;

    /**
     * Thời gian sống của cache (phút).
     */
    @Value("${jev.cache-ttl-minutes:60}")
    private int cacheTtlMinutes = 60;

    /** Pause remote calls after a service failure; cached answers remain usable. */
    @Value("${jev.failure-cooldown-ms:30000}")
    private int failureCooldownMs = 30000;

    @PostConstruct
    public void init() {
        if (apiUrl == null || apiUrl.isBlank()) {
            this.apiUrl = "https://api.typesafe.ai/v1/systemone";
        }
        if (model == null || model.isBlank()) {
            this.model = "jev-latest";
        }
        if (timeoutMs <= 0) {
            this.timeoutMs = 2500;
        }
        if (failureCooldownMs < 0) {
            this.failureCooldownMs = 30000;
        }

        // Nếu chưa cấu hình apiKey qua application.yml, kiểm tra biến môi trường
        if (apiKey == null || apiKey.isBlank()) {
            String envKey = System.getenv("TYPESAFE_API_KEY");
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getenv("JEV_API_KEY");
            }
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getProperty("jev.api-key");
            }
            if (envKey != null && !envKey.isBlank()) {
                this.apiKey = envKey.trim();
            }
        }
        if (apiKey != null) {
            this.apiKey = apiKey.trim();
        }
    }

    /**
     * Kiểm tra nhanh xem Jev có khả dụng để gọi hay không.
     */
    public boolean isOperational() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
