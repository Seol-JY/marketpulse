package pro.seol.marketpulse.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

class TradeEventTest {

    private static final Instant TRADED_AT = Instant.parse("2026-09-16T01:25:44.988Z");
    private static final Instant INGESTED_AT = Instant.parse("2026-09-16T01:25:45.130Z");

    private static TradeEvent trade(final String symbol, final String price, final String volume) {
        return new TradeEvent(
                Market.US,
                symbol,
                new BigDecimal(price),
                new BigDecimal(volume),
                TRADED_AT,
                INGESTED_AT,
                "prod-a",
                "c-17f3a2",
                1847263L);
    }

    @Nested
    @DisplayName("체결 이벤트를 만들 때")
    class Create {

        @Nested
        @DisplayName("성공")
        class Success {

            @Test
            void 파티션_키는_시장과_심볼을_이어붙인다() {
                // given
                TradeEvent event = trade("NVDA", "959.38", "4");

                // when
                String key = event.partitionKey();

                // then
                assertThat(key).isEqualTo("US:NVDA");
            }

            @Test
            void 수신_지연은_체결시각과_적재시각의_차이다() {
                // given
                TradeEvent event = trade("NVDA", "959.38", "4");

                // when
                long lag = event.ingestLagMillis();

                // then
                assertThat(lag).isEqualTo(142L);
            }

            @Test
            void 소수점_가격이_오차없이_보존된다() {
                // given
                TradeEvent event = trade("NVDA", "330.70", "1");

                // when & then
                assertSoftly(softly -> {
                    softly.assertThat(event.price()).isEqualByComparingTo("330.70");
                    softly.assertThat(event.price().toPlainString()).isEqualTo("330.70");
                });
            }
        }

        @Nested
        @DisplayName("실패")
        class Fail {

            @Test
            void 심볼이_비어있으면_거부한다() {
                // when & then
                assertThatThrownBy(() -> trade("  ", "959.38", "4"))
                        .isInstanceOf(PipelineException.class)
                        .extracting(e -> ((PipelineException) e).getExceptionCode())
                        .isEqualTo(ExceptionCode.INVALID_SYMBOL);
            }

            @Test
            void 가격이_0이면_거부한다() {
                // when & then
                assertThatThrownBy(() -> trade("NVDA", "0", "4"))
                        .isInstanceOf(PipelineException.class)
                        .extracting(e -> ((PipelineException) e).getExceptionCode())
                        .isEqualTo(ExceptionCode.INVALID_PRICE);
            }

            @Test
            void 수량이_음수면_거부한다() {
                // when & then
                assertThatThrownBy(() -> trade("NVDA", "959.38", "-1"))
                        .isInstanceOf(PipelineException.class)
                        .extracting(e -> ((PipelineException) e).getExceptionCode())
                        .isEqualTo(ExceptionCode.INVALID_VOLUME);
            }
        }
    }
}
