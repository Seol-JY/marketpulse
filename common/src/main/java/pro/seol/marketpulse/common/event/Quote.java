package pro.seol.marketpulse.common.event;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

import pro.seol.marketpulse.common.exception.ExceptionCode;

// 계산은 BigDecimal, 전송은 문자열. 소수점 오차 방지
public record Quote(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume) {

    public Quote {
        EventFields.requirePositive(price, ExceptionCode.INVALID_PRICE);
        EventFields.requirePositive(volume, ExceptionCode.INVALID_VOLUME);
    }
}
