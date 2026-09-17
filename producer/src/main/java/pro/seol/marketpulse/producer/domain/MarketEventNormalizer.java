package pro.seol.marketpulse.producer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import tools.jackson.databind.JsonNode;

import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.Quote;
import pro.seol.marketpulse.common.event.TradeEvent;
import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

public final class MarketEventNormalizer {

    // 전체 단계는 저장 비용이 커 상위 몇 단계만 유지
    public static final int MAX_DEPTH = 3;

    private MarketEventNormalizer() {}

    public static MarketEvent normalize(
            final TossMessage.Data data,
            final Instant ingestedAt,
            final String producerId,
            final String connectionId,
            final long seq) {
        return switch (data.topic().channel()) {
            case TRADE -> toTrade(data, ingestedAt, producerId, connectionId, seq);
            case ORDERBOOK -> toOrderbook(data, ingestedAt, producerId, connectionId, seq);
        };
    }

    private static TradeEvent toTrade(
            final TossMessage.Data data,
            final Instant ingestedAt,
            final String producerId,
            final String connectionId,
            final long seq) {
        final JsonNode payload = data.payload();
        return new TradeEvent(
                data.topic().market(),
                data.topic().symbol(),
                decimal(payload, "price"),
                decimal(payload, "volume"),
                timestamp(payload, "timestamp"),
                ingestedAt,
                producerId,
                connectionId,
                seq);
    }

    private static OrderbookEvent toOrderbook(
            final TossMessage.Data data,
            final Instant ingestedAt,
            final String producerId,
            final String connectionId,
            final long seq) {
        final JsonNode payload = data.payload();
        final List<Quote> asks = quotes(payload.path("asks"));
        final List<Quote> bids = quotes(payload.path("bids"));
        return new OrderbookEvent(
                data.topic().market(),
                data.topic().symbol(),
                Math.max(asks.size(), bids.size()),
                asks,
                bids,
                timestamp(payload, "timestamp"),
                ingestedAt,
                producerId,
                connectionId,
                seq);
    }

    private static List<Quote> quotes(final JsonNode array) {
        return array.valueStream()
                .limit(MAX_DEPTH)
                .map(node -> new Quote(decimal(node, "price"), decimal(node, "volume")))
                .toList();
    }

    private static BigDecimal decimal(final JsonNode node, final String field) {
        final String value = node.path(field).asString(null);
        if (value == null) {
            throw PipelineException.from(ExceptionCode.MALFORMED_PAYLOAD);
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw PipelineException.from(ExceptionCode.MALFORMED_PAYLOAD, e);
        }
    }

    private static Instant timestamp(final JsonNode node, final String field) {
        final String value = node.path(field).asString(null);
        if (value == null) {
            throw PipelineException.from(ExceptionCode.MALFORMED_PAYLOAD);
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            throw PipelineException.from(ExceptionCode.MALFORMED_PAYLOAD, e);
        }
    }
}
