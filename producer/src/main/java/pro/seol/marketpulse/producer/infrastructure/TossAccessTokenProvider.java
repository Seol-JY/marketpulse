package pro.seol.marketpulse.producer.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pro.seol.marketpulse.producer.application.AccessTokenProvider;
import pro.seol.marketpulse.producer.config.TossProperties;

@Slf4j
@Component
class TossAccessTokenProvider implements AccessTokenProvider {

    // 만료 직전 재발급은 재연결 시점과 겹칠 위험. 미리 교체
    private static final Duration RENEW_MARGIN = Duration.ofMinutes(30);

    private final RestClient restClient;
    private final TossProperties properties;

    private volatile String cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    TossAccessTokenProvider(final TossProperties properties) {
        this.restClient = RestClient.create(properties.baseUrl());
        this.properties = properties;
    }

    @Override
    public synchronized String token() {
        if (cached != null && Instant.now().isBefore(expiresAt.minus(RENEW_MARGIN))) {
            return cached;
        }
        return issue();
    }

    private String issue() {
        final Map<?, ?> body = restClient
                .post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("grant_type=client_credentials&client_id=%s&client_secret=%s"
                        .formatted(properties.clientId(), properties.clientSecret()))
                .retrieve()
                .body(Map.class);
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("토큰 응답에 access_token 이 없음");
        }
        cached = body.get("access_token").toString();
        final Object expires = body.get("expires_in");
        final long expiresIn = expires == null ? 86399L : Long.parseLong(String.valueOf(expires));
        expiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("토큰 발급 만료={} 남은시간={}초", expiresAt, expiresIn);
        return cached;
    }
}
