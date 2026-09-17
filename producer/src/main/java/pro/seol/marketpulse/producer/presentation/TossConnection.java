package pro.seol.marketpulse.producer.presentation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TossConnection {

    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final String name;
    private final URI uri;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final TokenSupplier tokens;
    private final BiConsumer<String, String> onMessage;
    private final Runnable onReconnect;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicReference<String> declaration = new AtomicReference<>();
    // 재연결마다 새로 발급. 겹침 구간을 사후에 찾는 유일한 단서
    private final AtomicReference<String> connectionId = new AtomicReference<>();
    private volatile Duration backoff = MIN_BACKOFF;

    interface TokenSupplier {
        String get();
    }

    TossConnection(
            final String name,
            final URI uri,
            final HttpClient httpClient,
            final ScheduledExecutorService scheduler,
            final TokenSupplier tokens,
            final BiConsumer<String, String> onMessage,
            final Runnable onReconnect) {
        this.name = name;
        this.uri = uri;
        this.httpClient = httpClient;
        this.scheduler = scheduler;
        this.tokens = tokens;
        this.onMessage = onMessage;
        this.onReconnect = onReconnect;
        newConnectionId();
    }

    String name() {
        return name;
    }

    String connectionId() {
        return connectionId.get();
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            connect();
        }
    }

    void stop() {
        running.set(false);
        final WebSocket current = socket.getAndSet(null);
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    void declare(final String payload) {
        declaration.set(payload);
        final WebSocket current = socket.get();
        if (current != null) {
            current.sendText(payload, true);
            log.info("구독 선언 connection={}", name);
        }
    }

    void ping() {
        final WebSocket current = socket.get();
        if (current != null) {
            current.sendText("PING", true);
        }
    }

    private void newConnectionId() {
        connectionId.set(name + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private void connect() {
        if (!running.get()) {
            return;
        }
        httpClient
                .newWebSocketBuilder()
                .header("Authorization", "Bearer " + tokens.get())
                .connectTimeout(HANDSHAKE_TIMEOUT)
                .buildAsync(uri, new Listener())
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        log.warn("연결 실패 connection={} 재시도={}초 후", name, backoff.toSeconds(), error);
                        scheduleReconnect();
                        return;
                    }
                    socket.set(ws);
                    backoff = MIN_BACKOFF;
                    log.info("연결 성공 connection={} connectionId={}", name, connectionId.get());
                    final String payload = declaration.get();
                    if (payload != null) {
                        ws.sendText(payload, true);
                    }
                });
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            return;
        }
        final Duration delay = backoff;
        backoff = Duration.ofSeconds(Math.min(backoff.toSeconds() * 2, MAX_BACKOFF.toSeconds()));
        scheduler.schedule(this::reconnect, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void reconnect() {
        newConnectionId();
        onReconnect.run();
        connect();
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
            buffer.append(data);
            if (last) {
                final String message = buffer.toString();
                buffer.setLength(0);
                onMessage.accept(message, connectionId.get());
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
            log.warn("연결 종료 connection={} status={} reason={}", name, statusCode, reason);
            socket.compareAndSet(webSocket, null);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(final WebSocket webSocket, final Throwable error) {
            log.warn("연결 오류 connection={}", name, error);
            socket.compareAndSet(webSocket, null);
            scheduleReconnect();
        }
    }
}
