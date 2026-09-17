package pro.seol.marketpulse.common.event;

import java.time.Instant;
import java.util.List;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

public record OrderbookEvent(
        Market market,
        String symbol,
        // 시장마다 단계 수가 달라 컨슈머의 분기를 대신함
        int depth,
        List<Quote> asks,
        List<Quote> bids,
        Instant quotedAt,
        Instant ingestedAt,
        String producerId,
        String connectionId,
        long seq)
        implements MarketEvent {

    public OrderbookEvent {
        EventFields.requireSymbol(symbol);
        EventFields.requireTimestamps(quotedAt, ingestedAt);
        EventFields.requireSequence(seq);
        asks = List.copyOf(asks);
        bids = List.copyOf(bids);
        if (asks.isEmpty() && bids.isEmpty()) {
            throw PipelineException.from(ExceptionCode.EMPTY_ORDERBOOK);
        }
    }

    @Override
    public Instant occurredAt() {
        return quotedAt;
    }

    public Quote bestAsk() {
        return asks.isEmpty() ? null : asks.getFirst();
    }

    public Quote bestBid() {
        return bids.isEmpty() ? null : bids.getFirst();
    }
}
