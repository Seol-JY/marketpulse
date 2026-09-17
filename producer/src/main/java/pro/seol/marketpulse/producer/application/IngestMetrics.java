package pro.seol.marketpulse.producer.application;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import pro.seol.marketpulse.producer.domain.Channel;

public class IngestMetrics {

    private final MeterRegistry registry;
    private final Timer ingestLag;

    public IngestMetrics(final MeterRegistry registry) {
        this.registry = registry;
        this.ingestLag = Timer.builder("marketpulse.ingest.lag")
                .description("체결·호가 발생 시각과 수신 시각의 차이")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void received(final Channel channel) {
        counter("marketpulse.received", channel).increment();
    }

    public void published(final Channel channel) {
        counter("marketpulse.published", channel).increment();
    }

    // 0 이 아니면 소스를 못 따라가는 중
    public void dropped(final Channel channel) {
        counter("marketpulse.dropped", channel).increment();
    }

    public void malformed() {
        registry.counter("marketpulse.malformed").increment();
    }

    public void tokenRefreshed(final String connection) {
        registry.counter("marketpulse.token.refresh", "connection", connection).increment();
    }

    public void reconnected(final String connection) {
        registry.counter("marketpulse.reconnect", "connection", connection).increment();
    }

    public void subscriptionRejected(final String connection, final int count) {
        registry.counter("marketpulse.subscription.rejected", "connection", connection)
                .increment(count);
    }

    public void lag(final long millis) {
        ingestLag.record(millis, TimeUnit.MILLISECONDS);
    }

    public void queueDepth(final Channel channel, final Supplier<Number> depth) {
        io.micrometer.core.instrument.Gauge.builder("marketpulse.queue.depth", depth)
                .tag("channel", channel.getCode())
                .register(registry);
    }

    private Counter counter(final String name, final Channel channel) {
        return registry.counter(name, "channel", channel.getCode());
    }
}
