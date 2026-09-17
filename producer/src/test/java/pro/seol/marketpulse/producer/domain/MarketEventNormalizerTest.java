package pro.seol.marketpulse.producer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.TradeEvent;
import pro.seol.marketpulse.common.exception.PipelineException;

class MarketEventNormalizerTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-09-16T01:25:45.130Z");

    // 토스 원본 메시지 형태
    private static final String TRADE_RAW = """
            {"type":"message","topic":"trade:us:NVDA",
             "data":{"price":"959.38","volume":"4","timestamp":"2026-09-16T10:25:44.988+09:00","currency":"USD"}}
            """;

    private static final String ORDERBOOK_RAW = """
            {"type":"message","topic":"orderbook:us:NVDA",
             "data":{"timestamp":"2026-09-16T10:25:45.122+09:00","currency":"USD",
                     "asks":[{"price":"330.74","volume":"160"}],
                     "bids":[{"price":"330.7","volume":"40"}]}}
            """;

    private static final String ORDERBOOK_KR_RAW = """
            {"type":"message","topic":"orderbook:kr:005930",
             "data":{"timestamp":"2026-09-16T09:03:12.345+09:00","currency":"KRW",
                     "asks":[{"price":"63300","volume":"10"},{"price":"63400","volume":"20"},
                             {"price":"63500","volume":"30"},{"price":"63600","volume":"40"},
                             {"price":"63700","volume":"50"}],
                     "bids":[{"price":"63200","volume":"11"},{"price":"63100","volume":"21"},
                             {"price":"63000","volume":"31"},{"price":"62900","volume":"41"},
                             {"price":"62800","volume":"51"}]}}
            """;

    private static TossMessage.Data parse(final String raw) {
        return (TossMessage.Data) TossMessage.from(raw);
    }

    @Nested
    @DisplayName("체결 메시지를 변환할 때")
    class Trade {

        @Test
        void 심볼을_토픽에서_꺼낸다() {
            // given
            TossMessage.Data data = parse(TRADE_RAW);

            // when
            TradeEvent event = (TradeEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "A-1", 1L);

            // then
            assertSoftly(softly -> {
                softly.assertThat(event.market()).isEqualTo(Market.US);
                softly.assertThat(event.symbol()).isEqualTo("NVDA");
                softly.assertThat(event.partitionKey()).isEqualTo("US:NVDA");
            });
        }

        @Test
        void 가격과_수량이_오차없이_옮겨진다() {
            // given
            TossMessage.Data data = parse(TRADE_RAW);

            // when
            TradeEvent event = (TradeEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "A-1", 1L);

            // then
            assertSoftly(softly -> {
                softly.assertThat(event.price()).isEqualByComparingTo("959.38");
                softly.assertThat(event.volume()).isEqualByComparingTo("4");
            });
        }

        @Test
        void 오프셋이_붙은_시각을_UTC_순간으로_바꾼다() {
            // given
            TossMessage.Data data = parse(TRADE_RAW);

            // when
            TradeEvent event = (TradeEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "A-1", 1L);

            // then
            assertThat(event.tradedAt()).isEqualTo(Instant.parse("2026-09-16T01:25:44.988Z"));
        }

        @Test
        void 봉투_필드가_채워진다() {
            // given
            TossMessage.Data data = parse(TRADE_RAW);

            // when
            TradeEvent event = (TradeEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "A-1", 42L);

            // then
            assertSoftly(softly -> {
                softly.assertThat(event.producerId()).isEqualTo("prod-a");
                softly.assertThat(event.connectionId()).isEqualTo("A-1");
                softly.assertThat(event.seq()).isEqualTo(42L);
                softly.assertThat(event.ingestLagMillis()).isEqualTo(142L);
            });
        }
    }

    @Nested
    @DisplayName("호가 메시지를 변환할 때")
    class Orderbook {

        @Test
        void 미국은_1단계_그대로다() {
            // given
            TossMessage.Data data = parse(ORDERBOOK_RAW);

            // when
            OrderbookEvent event =
                    (OrderbookEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "B-1", 1L);

            // then
            assertSoftly(softly -> {
                softly.assertThat(event.depth()).isEqualTo(1);
                softly.assertThat(event.bestAsk().price()).isEqualByComparingTo("330.74");
                softly.assertThat(event.bestBid().price()).isEqualByComparingTo("330.7");
            });
        }

        @Test
        void 국내_10단계는_상위_3단계만_남긴다() {
            // given
            TossMessage.Data data = parse(ORDERBOOK_KR_RAW);

            // when
            OrderbookEvent event =
                    (OrderbookEvent) MarketEventNormalizer.normalize(data, INGESTED_AT, "prod-a", "B-1", 1L);

            // then
            assertSoftly(softly -> {
                softly.assertThat(event.asks()).hasSize(3);
                softly.assertThat(event.bids()).hasSize(3);
                softly.assertThat(event.depth()).isEqualTo(3);
                softly.assertThat(event.bestAsk().price()).isEqualByComparingTo("63300");
            });
        }
    }

    @Nested
    @DisplayName("깨진 메시지")
    class Malformed {

        @Test
        void 가격이_없으면_거부한다() {
            // given
            TossMessage.Data data =
                    parse("{\"type\":\"message\",\"topic\":\"trade:us:NVDA\",\"data\":{\"volume\":\"4\"}}");

            // when & then
            assertThatThrownBy(() -> MarketEventNormalizer.normalize(data, INGESTED_AT, "p", "c", 1L))
                    .isInstanceOf(PipelineException.class);
        }
    }
}
