package dev.streamledger.client;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public InfraiException(String code, String detail, int httpStatus) {
        super(detail);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
