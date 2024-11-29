package com.fas.dentistry_data_analysis.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorServiceConfig {

    @Bean
    public ExecutorService executorService() {
        // FixedThreadPool을 사용하여 ExecutorService를 반환합니다.
        return Executors.newFixedThreadPool(10);  // 10개의 스레드 풀로 설정
    }
}
