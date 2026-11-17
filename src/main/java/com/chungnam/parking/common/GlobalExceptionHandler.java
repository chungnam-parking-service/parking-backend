package com.chungnam.parking.common;

import jakarta.validation.ConstraintViolationException;
import com.chungnam.parking.place.PlaceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlaceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlaceNotFound(PlaceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "목적지를 찾을 수 없습니다.");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,   // 깨진 JSON 본문
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "'" + e.getName() + "' 값이 올바르지 않습니다: " + e.getValue());
    }

    /**
     * 없는 필드로 정렬 요청 (예: sort=foo,asc).
     * 레포지토리 프록시가 InvalidDataAccessApiUsageException 으로 감싸서 던지기도 하므로 원인까지 확인한다.
     */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ErrorResponse> handleInvalidSort(Exception e) {
        Throwable t = e;
        while (t != null && !(t instanceof PropertyReferenceException)) {
            t = t.getCause();
        }
        if (t instanceof PropertyReferenceException pre) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                    "정렬 기준 '" + pre.getPropertyName() + "' 은(는) 사용할 수 없습니다. "
                            + "예: name,asc / totalSpaces,desc / region,asc");
        }
        return handleUnknown(e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        // 없는 주소(404), 지원하지 않는 HTTP 메서드(405) 등 Spring MVC 표준 예외는
        // 원래 상태코드를 그대로 돌려준다. (500 으로 뭉개지지 않도록)
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            String message = status == HttpStatus.NOT_FOUND ? "요청한 주소를 찾을 수 없습니다." : e.getMessage();
            return error(status, status.name(), message);
        }
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message));
    }
}
