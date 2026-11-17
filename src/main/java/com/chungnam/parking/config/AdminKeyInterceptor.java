package com.chungnam.parking.config;

import com.chungnam.parking.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/admin/** 공통 인증. 헤더 X-Admin-Key 가 ADMIN_API_KEY 와 같아야 통과한다.
 * 키가 설정되지 않은 서버는 모든 관리자 요청을 거부한다.
 */
public class AdminKeyInterceptor implements HandlerInterceptor {

    private final byte[] expected;

    public AdminKeyInterceptor(String adminApiKey) {
        this.expected = adminApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;   // CORS preflight 는 키 없이 통과
        }
        String key = request.getHeader("X-Admin-Key");
        if (expected.length == 0 || key == null
                || !MessageDigest.isEqual(expected, key.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("관리자 키가 올바르지 않습니다.");
        }
        return true;
    }
}
