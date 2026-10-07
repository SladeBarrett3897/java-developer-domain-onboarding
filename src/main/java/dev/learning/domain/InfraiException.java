package dev.learning.domain;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int statusCode;

    public InfraiException(String code, String detail, int statusCode) {
        super(detail == null || detail.isBlank() ? code : code + ": " + detail);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String code() {
        return code;
    }

    public int statusCode() {
        return statusCode;
    }
}
