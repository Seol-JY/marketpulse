package pro.seol.marketpulse.common.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.Quote;
import pro.seol.marketpulse.common.event.TradeEvent;

class EventJsonTest {

    private static final Instant TRADED_AT = Instant.parse("2026-09-16T01:25:44.988Z");
    private static final Instant INGESTED_AT = Instant.parse("2026-09-16T01:25:45.130Z");

    private static TradeEvent sampleTrade() {
        return new TradeEvent(
                Market.US,
                "NVDA",
                new BigDecimal("959.38"),
                new BigDecimal("4"),
                TRADED_AT,
                INGESTED_AT,
                "prod-a",
                "c-17f3a2",
                1847263L);
    }

    @Nested
    @DisplayName("체결 이벤트 직렬화")
    class TradeSerde {

        @Test
        void 가격과_수량은_문자열로_나간다() {
            // given
            TradeEvent event = sampleTrade();

            // when
            String json = new String(EventJson.toBytes(event), StandardCharsets.UTF_8);

            // then
            assertSoftly(softly -> {
                softly.assertThat(json).contains("\"price\":\"959.38\"");
                softly.assertThat(json).contains("\"volume\":\"4\"");
            });
        }

        @Test
        void 시각은_ISO_8601_로_나간다() {
            // given
            TradeEvent event = sampleTrade();

            // when
            String json = new String(EventJson.toBytes(event), StandardCharsets.UTF_8);

            // then
            assertThat(json).contains("\"tradedAt\":\"2026-09-16T01:25:44.988Z\"");
        }

        @Test
        void 왕복해도_값이_보존된다() {
            // given
            TradeEvent event = sampleTrade();

            // when
            TradeEvent restored = EventJson.fromBytes(EventJson.toBytes(event), TradeEvent.class);

            // then
            assertSoftly(softly -> {
                softly.assertThat(restored.market()).isEqualTo(Market.US);
                softly.assertThat(restored.symbol()).isEqualTo("NVDA");
                softly.assertThat(restored.price()).isEqualByComparingTo("959.38");
                softly.assertThat(restored.tradedAt()).isEqualTo(TRADED_AT);
                softly.assertThat(restored.seq()).isEqualTo(1847263L);
            });
        }

        @Test
        void 모르는_필드가_있어도_읽는다() {
            // given
            String json = """
                    {"market":"US","symbol":"NVDA","price":"959.38","volume":"4",
                     "tradedAt":"2026-09-16T01:25:44.988Z","ingestedAt":"2026-09-16T01:25:45.130Z",
                     "producerId":"prod-a","connectionId":"c-17f3a2","seq":1847263,
                     "futureField":"신규 프로듀서가 추가한 필드"}
                    """;

            // when
            TradeEvent restored = EventJson.fromBytes(json.getBytes(StandardCharsets.UTF_8), TradeEvent.class);

            // then
            assertThat(restored.symbol()).isEqualTo("NVDA");
        }
    }

    @Nested
    @DisplayName("호가 이벤트 직렬화")
    class OrderbookSerde {

        @Test
        void 호가_목록이_왕복해도_보존된다() {
            // given
            OrderbookEvent event = new OrderbookEvent(
                    Market.US,
                    "NVDA",
                    1,
                    List.of(new Quote(new BigDecimal("330.74"), new BigDecimal("160"))),
                    List.of(new Quote(new BigDecimal("330.70"), new BigDecimal("40"))),
                    TRADED_AT,
                    INGESTED_AT,
                    "prod-a",
                    "c-17f3a2",
                    1847264L);

            // when
            OrderbookEvent restored = EventJson.fromBytes(EventJson.toBytes(event), OrderbookEvent.class);

            // then
            assertSoftly(softly -> {
                softly.assertThat(restored.depth()).isEqualTo(1);
                softly.assertThat(restored.bestAsk().price()).isEqualByComparingTo("330.74");
                softly.assertThat(restored.bestBid().volume()).isEqualByComparingTo("40");
                softly.assertThat(restored.partitionKey()).isEqualTo("US:NVDA");
            });
        }
    }
}
