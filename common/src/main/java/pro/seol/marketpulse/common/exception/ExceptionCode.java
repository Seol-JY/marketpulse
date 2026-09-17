package pro.seol.marketpulse.common.exception;

public enum ExceptionCode {
    INVALID_SYMBOL("EVT-001", "심볼이 비어 있거나 형식이 올바르지 않음"),
    INVALID_PRICE("EVT-002", "가격이 없거나 0 이하"),
    INVALID_VOLUME("EVT-003", "수량이 없거나 0 이하"),
    INVALID_SEQUENCE("EVT-004", "시퀀스 번호가 0 이하"),
    EMPTY_ORDERBOOK("EVT-005", "매수·매도 호가가 모두 없음"),
    MISSING_TIMESTAMP("EVT-006", "타임스탬프 누락"),
    UNKNOWN_CHANNEL("EVT-007", "알 수 없는 채널"),
    MALFORMED_TOPIC("EVT-008", "토픽 문자열 형식이 올바르지 않음"),
    MALFORMED_PAYLOAD("EVT-009", "페이로드에 필수 필드가 없거나 형식이 다름"),
    SERIALIZATION_FAILED("SER-001", "이벤트 직렬화 실패"),
    DESERIALIZATION_FAILED("SER-002", "이벤트 역직렬화 실패");

    private final String code;
    private final String message;

    ExceptionCode(final String code, final String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
