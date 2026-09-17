package pro.seol.marketpulse.common.event;

import java.time.Instant;

import pro.seol.marketpulse.common.Market;

public sealed interface MarketEvent permits TradeEvent, OrderbookEvent {

    Market market();

    String symbol();

    Instant occurredAt();

    Instant ingestedAt();

    long seq();

    default String partitionKey() {
        return market().name() + ":" + symbol();
    }

    default long ingestLagMillis() {
        return ingestedAt().toEpochMilli() - occurredAt().toEpochMilli();
    }
}
