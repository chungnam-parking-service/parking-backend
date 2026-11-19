package com.chungnam.parking.assistant;

/** AI 서버 미설정·장애. 503 으로 응답한다. */
public class AssistantUnavailableException extends RuntimeException {

    private final String code;

    public AssistantUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
