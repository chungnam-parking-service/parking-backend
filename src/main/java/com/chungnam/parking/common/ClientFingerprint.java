package com.chungnam.parking.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 같은 사용자의 중복 피드백·과도한 제보를 막기 위한 식별값.
 * IP 를 그대로 저장하지 않고 비밀 값(salt)과 함께 SHA-256 으로 바꾼 값만 저장한다.
 * CLIENT_HASH_SALT 를 주지 않으면 서버를 켤 때마다 새로 만든다 (재시작하면 중복 판정이 초기화됨).
 * 프록시 뒤라면 X-Forwarded-For 의 첫 주소를 쓴다. 조작할 수 있으므로 강한 보안 장치가 아니라 남용 완화용이다.
 */
@Component
public class ClientFingerprint {

    private final byte[] salt;

    public ClientFingerprint(@Value("${client-hash.salt:${CLIENT_HASH_SALT:}}") String configuredSalt) {
        if (configuredSalt == null || configuredSalt.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.salt = random;
        } else {
            this.salt = configuredSalt.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return HexFormat.of().formatHex(digest.digest(String.valueOf(ip).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
