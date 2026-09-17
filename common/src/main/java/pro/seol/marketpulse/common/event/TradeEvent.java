package pro.seol.marketpulse.common.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.exception.ExceptionCode;

public record TradeEvent(
        Market market,
        String symbol,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume,
        Instant tradedAt,
        Instant ingestedAt,
        String producerId,
        // 재연결 겹침 구간 사후 식별용
        String connectionId,
        long seq)
        implements MarketEvent {

    public TradeEvent {
        EventFields.requireSymbol(symbol);
        EventFields.requirePositive(price, ExceptionCode.INVALID_PRICE);
        EventFields.requirePositive(volume, ExceptionCode.INVALID_VOLUME);
        EventFields.requireTimestamps(tradedAt, ingestedAt);
        EventFields.requireSequence(seq);
    }

    @Override
    public Instant occurredAt() {
        return tradedAt;
    }
}
