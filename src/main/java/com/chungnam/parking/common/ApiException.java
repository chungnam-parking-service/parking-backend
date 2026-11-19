package com.chungnam.parking.common;

import org.springframework.http.HttpStatus;

/**
 * 상태 코드와 오류 code 를 직접 정해서 던지는 예외. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다.
 * 예) new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FEEDBACK", "...")
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
