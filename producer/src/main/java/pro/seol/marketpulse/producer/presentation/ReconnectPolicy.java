package pro.seol.marketpulse.producer.presentation;

import java.time.Duration;

// 재연결 간격과 토큰 재발급 시점. 외부 접속 규칙이라 어댑터에 둠
public record ReconnectPolicy(Duration minBackoff, Duration maxBackoff, int tokenRefreshAfterFailures) {

    public static final ReconnectPolicy DEFAULT = new ReconnectPolicy(Duration.ofSeconds(1), Duration.ofSeconds(60), 2);

    public Duration nextBackoff(final Duration current) {
        final long doubled = current.toSeconds() * 2;
        return Duration.ofSeconds(Math.min(doubled, maxBackoff.toSeconds()));
    }

    public boolean shouldRefreshToken(final int consecutiveFailures) {
        return consecutiveFailures >= tokenRefreshAfterFailures;
    }
}
