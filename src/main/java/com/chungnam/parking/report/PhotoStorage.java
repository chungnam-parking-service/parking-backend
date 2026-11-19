package com.chungnam.parking.report;

import com.chungnam.parking.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * 입구 사진을 로컬 폴더(uploads.dir)에 저장한다. GET /uploads/{파일명} 으로 공개된다.
 * - JPEG·PNG·WebP 만 받는다. 확장자나 Content-Type 이 아니라 파일 앞부분(매직 바이트)으로 판단한다.
 * - 파일명은 서버가 만든 무작위 UUID (사용자가 보낸 이름은 쓰지 않는다).
 * - 크기 제한은 spring.servlet.multipart.max-file-size (5MB).
 * 배포 플랫폼의 로컬 디스크는 재배포 때 지워질 수 있어서, 운영에서는 S3 같은 저장소로 바꾸는 것을 권한다.
 */
@Slf4j
@Component
public class PhotoStorage {

    public static final String URL_PREFIX = "/uploads/";

    private final Path dir;

    public PhotoStorage(@Value("${uploads.dir:uploads}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path dir() {
        return dir;
    }

    /** 저장하고 "/uploads/xxx.jpg" 경로를 돌려준다 */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String extension;
        try (InputStream in = file.getInputStream()) {
            extension = detect(in.readNBytes(12));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "사진을 읽지 못했습니다.");
        }
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "사진은 JPG, PNG, WebP 만 올릴 수 있어요.");
        }
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(name));
            }
        } catch (IOException e) {
            log.error("사진 저장 실패", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_SAVE_FAILED", "사진을 저장하지 못했습니다.");
        }
        return URL_PREFIX + name;
    }

    public void delete(String path) {
        if (path == null || !path.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = dir.resolve(path.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(dir)) {
            return;   // 폴더 밖 경로는 무시
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("사진 삭제 실패: {}", path);
        }
    }

    /** 파일 앞부분으로 형식 판단. 모르는 형식이면 null */
    static String detect(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "png";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
