package com.chungnam.parking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${admin.api-key:}")
    private String adminApiKey;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminKeyInterceptor(adminApiKey)).addPathPatterns("/api/admin/**");
    }

    @Value("${uploads.dir:uploads}")
    private String uploadsDir;

    /** 승인 전·후 제보 사진을 GET /uploads/{무작위 파일명} 으로 내보낸다 (파일명은 서버가 만든 UUID) */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = java.nio.file.Path.of(uploadsDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCachePeriod(86_400);
    }

    /** 운영시간 판단 등에 사용 (테스트에서 고정 시각으로 교체 가능) */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    /** 프론트엔드 개발 서버에서 API 호출 허용 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .maxAge(3600);
        // 프론트 API.md 계약 경로 (/api 접두사 없음)
        // GET: 검색·상세 / POST: 정확도 피드백, 주차 정보 제보
        registry.addMapping("/places/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .maxAge(3600);
        registry.addMapping("/assistant/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "OPTIONS")
                .maxAge(3600);
    }
}
