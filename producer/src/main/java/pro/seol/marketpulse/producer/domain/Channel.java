package pro.seol.marketpulse.producer.domain;

import java.util.Arrays;

import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

public enum Channel {
    TRADE("trade"),
    ORDERBOOK("orderbook");

    private final String code;

    Channel(final String code) {
        this.code = code;
    }

    public static Channel from(final String code) {
        return Arrays.stream(values())
                .filter(channel -> channel.code.equals(code))
                .findFirst()
                .orElseThrow(() -> PipelineException.from(ExceptionCode.UNKNOWN_CHANNEL));
    }

    public String getCode() {
        return code;
    }
}
