package pro.seol.marketpulse.producer.presentation;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.producer.application.AccessTokenProvider;
import pro.seol.marketpulse.producer.application.IngestMetrics;
import pro.seol.marketpulse.producer.application.MarketIngestUseCase;
import pro.seol.marketpulse.producer.config.TossProperties;

@Slf4j
@Component
// 테스트와 점검 배포에서 외부 연결 없이 띄울 수 있게 함
@ConditionalOnProperty(name = "producer.enabled", havingValue = "true", matchIfMissing = true)
class MarketDataRunner implements SmartLifecycle {

    // 토스 유휴 제한보다 짧게
    private static final Duration PING_INTERVAL = Duration.ofSeconds(50);
    private static final Duration SESSION_CHECK_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DRAIN_SHUTDOWN_TIMEOUT = Duration.ofSeconds(20);

    private final MarketIngestUseCase useCase;
    private final IngestMetrics metrics;
    private final AccessTokenProvider tokens;
    private final TossProperties properties;

    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, String> declaredBy = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private Thread drainThread;
    private HttpClient httpClient;
    private List<TossConnection> connections = List.of();

    MarketDataRunner(
            final MarketIngestUseCase useCase,
            final IngestMetrics metrics,
            final AccessTokenProvider tokens,
            final TossProperties properties) {
        this.useCase = useCase;
        this.metrics = metrics;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        scheduler = Executors.newScheduledThreadPool(
                2, Thread.ofPlatform().name("toss-", 0).factory());
        connections = List.of(connection("A"), connection("B"));
        connections.forEach(TossConnection::start);

        scheduler.scheduleAtFixedRate(
                this::pingAll, PING_INTERVAL.toSeconds(), PING_INTERVAL.toSeconds(), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::syncSubscriptions, 2, SESSION_CHECK_INTERVAL.toSeconds(), TimeUnit.SECONDS);

        drainThread = Thread.ofPlatform().name("kafka-drain").start(this::drainLoop);
        log.info("프로듀서 시작 종목={}개", properties.symbols().size());
    }

    private TossConnection connection(final String name) {
        return new TossConnection(
                name,
                URI.create(properties.wsUrl()),
                httpClient,
                scheduler,
                tokens::token,
                useCase::onMessage,
                () -> metrics.reconnected(name));
    }

    private void pingAll() {
        connections.forEach(TossConnection::ping);
    }

    // 개장·마감 때만 선언. 장중 갱신은 겹침 중복만 만듦
    private void syncSubscriptions() {
        for (final TossConnection connection : connections) {
            final String payload = useCase.declarationFor(connection.name()).orElse(null);
            if (payload == null) {
                declaredBy.remove(connection.name());
                continue;
            }
            if (!payload.equals(declaredBy.get(connection.name()))) {
                declaredBy.put(connection.name(), payload);
                connection.declare(payload);
            }
        }
    }

    private void drainLoop() {
        while (running.get() || useCase.queueDepth() > 0) {
            try {
                final MarketEvent event = useCase.drainOne();
                if (event != null) {
                    useCase.publish(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("전송 루프 오류", e);
            }
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        connections.forEach(TossConnection::stop);
        scheduler.shutdownNow();
        awaitDrain();
        final int flushed = useCase.flushRemaining();
        log.info("프로듀서 종료 잔여 전송={}건", flushed);
    }

    private void awaitDrain() {
        try {
            drainThread.join(DRAIN_SHUTDOWN_TIMEOUT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // 카프카 템플릿보다 먼저 정지해야 잔여 이벤트 배출 가능
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
