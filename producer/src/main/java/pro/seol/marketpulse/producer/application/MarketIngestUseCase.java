package pro.seol.marketpulse.producer.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.TradeEvent;
import pro.seol.marketpulse.common.exception.PipelineException;
import pro.seol.marketpulse.producer.domain.Channel;
import pro.seol.marketpulse.producer.domain.MarketCalendar;
import pro.seol.marketpulse.producer.domain.MarketEventNormalizer;
import pro.seol.marketpulse.producer.domain.SubscriptionPlan;
import pro.seol.marketpulse.producer.domain.TossMessage;

@Slf4j
public class MarketIngestUseCase {

    private static final long DRAIN_POLL_MILLIS = 100L;

    private final MarketEventPublisher publisher;
    private final IngestMetrics metrics;
    private final Clock clock;
    private final String producerId;
    private final Map<Market, List<String>> symbols;

    // 큐 분리. 넘치면 호가만 폐기하고 체결은 보존
    private final BlockingQueue<MarketEvent> tradeQueue;
    private final BlockingQueue<MarketEvent> orderbookQueue;
    private final AtomicLong sequence = new AtomicLong();

    public MarketIngestUseCase(
            final MarketEventPublisher publisher,
            final IngestMetrics metrics,
            final Clock clock,
            final String producerId,
            final Map<Market, List<String>> symbols,
            final int tradeQueueSize,
            final int orderbookQueueSize) {
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
        this.producerId = producerId;
        this.symbols = Map.copyOf(symbols);
        this.tradeQueue = new ArrayBlockingQueue<>(tradeQueueSize);
        this.orderbookQueue = new ArrayBlockingQueue<>(orderbookQueueSize);
        metrics.queueDepth(Channel.TRADE, tradeQueue::size);
        metrics.queueDepth(Channel.ORDERBOOK, orderbookQueue::size);
    }

    public Optional<String> declarationFor(final String connectionId) {
        return MarketCalendar.openAt(clock.instant())
                .flatMap(market -> SubscriptionPlan.forMarket(market, symbols.getOrDefault(market, List.of())).stream()
                        .filter(plan -> !plan.symbols().isEmpty())
                        .filter(plan -> plan.connectionId().equals(connectionId))
                        .findFirst())
                .map(SubscriptionPlan::declaration);
    }

    // WebSocket 수신 스레드. 블로킹 금지
    public void onMessage(final String raw, final String connectionId) {
        switch (TossMessage.from(raw)) {
            case TossMessage.Data data -> enqueue(data, connectionId);
            case TossMessage.SubscriptionAck ack -> onAck(ack, connectionId);
            case TossMessage.Failure failure ->
                log.warn("토스 오류 응답 connection={} code={} message={}", connectionId, failure.code(), failure.message());
            case TossMessage.Unknown unknown -> metrics.malformed();
        }
    }

    private void onAck(final TossMessage.SubscriptionAck ack, final String connectionId) {
        log.info(
                "구독 확정 connection={} 수락={} 거부={}",
                connectionId,
                ack.subscribed().size(),
                ack.rejected().size());
        if (!ack.rejected().isEmpty()) {
            metrics.subscriptionRejected(connectionId, ack.rejected().size());
        }
    }

    private void enqueue(final TossMessage.Data data, final String connectionId) {
        final Channel channel = data.topic().channel();
        final MarketEvent event;
        try {
            event = MarketEventNormalizer.normalize(
                    data, clock.instant(), producerId, connectionId, sequence.incrementAndGet());
        } catch (PipelineException e) {
            metrics.malformed();
            return;
        }
        metrics.received(channel);
        metrics.lag(event.ingestLagMillis());
        if (!queueOf(channel).offer(event)) {
            metrics.dropped(channel);
        }
    }

    private BlockingQueue<MarketEvent> queueOf(final Channel channel) {
        return channel == Channel.TRADE ? tradeQueue : orderbookQueue;
    }

    public MarketEvent drainOne() throws InterruptedException {
        final MarketEvent trade = tradeQueue.poll();
        if (trade != null) {
            return trade;
        }
        return orderbookQueue.poll(DRAIN_POLL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void publish(final MarketEvent event) {
        publisher.publish(event);
        metrics.published(channelOf(event));
    }

    // LOSSY 소스라 폐기하면 복구 불가
    public int flushRemaining() {
        int flushed = 0;
        MarketEvent event = tradeQueue.poll();
        while (event != null) {
            publish(event);
            flushed++;
            event = tradeQueue.poll();
        }
        event = orderbookQueue.poll();
        while (event != null) {
            publish(event);
            flushed++;
            event = orderbookQueue.poll();
        }
        publisher.flush();
        return flushed;
    }

    public int queueDepth() {
        return tradeQueue.size() + orderbookQueue.size();
    }

    private Channel channelOf(final MarketEvent event) {
        return switch (event) {
            case TradeEvent ignored -> Channel.TRADE;
            case OrderbookEvent ignored -> Channel.ORDERBOOK;
        };
    }
}
