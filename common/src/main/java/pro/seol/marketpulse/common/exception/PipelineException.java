package pro.seol.marketpulse.common.exception;

public final class PipelineException extends RuntimeException {

    private final transient ExceptionCode exceptionCode;

    private PipelineException(final ExceptionCode exceptionCode, final Throwable cause) {
        super("[%s] %s".formatted(exceptionCode.getCode(), exceptionCode.getMessage()), cause);
        this.exceptionCode = exceptionCode;
    }

    public static PipelineException from(final ExceptionCode exceptionCode) {
        return new PipelineException(exceptionCode, null);
    }

    public static PipelineException from(final ExceptionCode exceptionCode, final Throwable cause) {
        return new PipelineException(exceptionCode, cause);
    }

    public ExceptionCode getExceptionCode() {
        return exceptionCode;
    }
}
