package pro.seol.marketpulse.producer.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReconnectPolicyTest {

    private final ReconnectPolicy policy = ReconnectPolicy.DEFAULT;

    @Nested
    @DisplayName("재연결 간격")
    class Backoff {

        @Test
        void 실패할수록_두_배씩_늘어난다() {
            // given
            Duration current = policy.minBackoff();

            // when
            Duration second = policy.nextBackoff(current);
            Duration third = policy.nextBackoff(second);

            // then
            assertSoftly(softly -> {
                softly.assertThat(second).isEqualTo(Duration.ofSeconds(2));
                softly.assertThat(third).isEqualTo(Duration.ofSeconds(4));
            });
        }

        @Test
        void 상한을_넘지_않는다() {
            // given
            Duration current = Duration.ofSeconds(40);

            // when
            Duration next = policy.nextBackoff(policy.nextBackoff(current));

            // then
            assertThat(next).isEqualTo(policy.maxBackoff());
        }
    }

    @Nested
    @DisplayName("토큰 재발급 판단")
    class TokenRefresh {

        @Test
        void 첫_실패에는_재발급하지_않는다() {
            // 일시적 네트워크 오류에 토큰까지 갈아엎지 않는다
            assertThat(policy.shouldRefreshToken(1)).isFalse();
        }

        @Test
        void 연속_두_번_실패하면_재발급한다() {
            // 토큰이 죽으면 핸드셰이크만 계속 실패한다
            assertThat(policy.shouldRefreshToken(2)).isTrue();
        }
    }
}
