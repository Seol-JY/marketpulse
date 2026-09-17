package pro.seol.marketpulse.producer.domain;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

// 심볼이 payload 에 없어 토픽 문자열에서 꺼내야 함
public record TossTopic(Channel channel, Market market, String symbol) {

    private static final int SEGMENTS = 3;

    public static TossTopic parse(final String raw) {
        if (raw == null) {
            throw PipelineException.from(ExceptionCode.MALFORMED_TOPIC);
        }
        final String[] parts = raw.split(":", SEGMENTS);
        if (parts.length != SEGMENTS || parts[2].isBlank()) {
            throw PipelineException.from(ExceptionCode.MALFORMED_TOPIC);
        }
        try {
            return new TossTopic(Channel.from(parts[0]), Market.valueOf(parts[1].toUpperCase()), parts[2]);
        } catch (IllegalArgumentException e) {
            throw PipelineException.from(ExceptionCode.MALFORMED_TOPIC, e);
        }
    }

    public String subscriptionType() {
        return channel.getCode() + ":" + market.name().toLowerCase();
    }
}
