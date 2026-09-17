package pro.seol.marketpulse.common.serde;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

public final class EventJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // 프로듀서가 필드를 추가해도 구버전 컨슈머가 죽지 않게 함
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private EventJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static byte[] toBytes(final Object event) {
        try {
            return MAPPER.writeValueAsBytes(event);
        } catch (JacksonException e) {
            throw PipelineException.from(ExceptionCode.SERIALIZATION_FAILED, e);
        }
    }

    public static <T> T fromBytes(final byte[] bytes, final Class<T> type) {
        try {
            return MAPPER.readValue(bytes, type);
        } catch (JacksonException e) {
            throw PipelineException.from(ExceptionCode.DESERIALIZATION_FAILED, e);
        }
    }
}
