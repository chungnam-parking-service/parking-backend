package com.chungnam.parking.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * "/uploads/x.jpg" 같은 서버 경로를 프론트가 쓸 수 있는 절대 URL 로 바꾼다 (프론트 스키마가 http(s) URL 만 받음).
 * PUBLIC_BASE_URL 이 있으면 그것을, 없으면 현재 요청의 주소(프록시 헤더 반영)를 쓴다.
 */
@Component
public class PublicUrl {

    private final String baseUrl;

    public PublicUrl(@Value("${app.public-base-url:${PUBLIC_BASE_URL:}}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public String of(String path) {
        if (path == null) {
            return null;
        }
        if (!baseUrl.isBlank()) {
            return baseUrl + path;
        }
        if (RequestContextHolder.getRequestAttributes() != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
        }
        return path;
    }
}
