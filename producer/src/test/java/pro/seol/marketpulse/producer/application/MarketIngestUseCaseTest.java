package pro.seol.marketpulse.producer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.event.TradeEvent;

class MarketIngestUseCaseTest {

    // 미국 정규장 한복판
    private static final Instant US_SESSION = Instant.parse("2026-09-16T14:30:00Z");
    private static final Clock CLOCK = Clock.fixed(US_SESSION, ZoneOffset.UTC);

    private static String trade(final String symbol, final String price) {
        return """
                {"type":"message","topic":"trade:us:%s",
                 "data":{"price":"%s","volume":"1","timestamp":"2026-09-16T23:29:59.900+09:00","currency":"USD"}}
                """.formatted(symbol, price);
    }

    private static String orderbook(final String symbol) {
        return """
                {"type":"message","topic":"orderbook:us:%s",
                 "data":{"timestamp":"2026-09-16T23:29:59.900+09:00","currency":"USD",
                         "asks":[{"price":"330.74","volume":"160"}],
                         "bids":[{"price":"330.70","volume":"40"}]}}
                """.formatted(symbol);
    }

    private static final class RecordingPublisher implements MarketEventPublisher {
        private final List<MarketEvent> published = new ArrayList<>();
        private int flushes;

        @Override
        public void publish(final MarketEvent event) {
            published.add(event);
        }

        @Override
        public void flush() {
            flushes++;
        }
    }

    private static MarketIngestUseCase useCase(
            final RecordingPublisher publisher, final int tradeQueue, final int orderbookQueue) {
        return new MarketIngestUseCase(
                publisher,
                new IngestMetrics(new SimpleMeterRegistry()),
                CLOCK,
                "prod-a",
                Map.of(Market.US, List.of("NVDA", "TSLA")),
                tradeQueue,
                orderbookQueue);
    }

    @Nested
    @DisplayName("큐가 넘칠 때")
    class QueueOverflow {

        @Test
        void 호가는_버리고_체결은_지킨다() throws Exception {
            // given
            RecordingPublisher publisher = new RecordingPublisher();
            MarketIngestUseCase sut = useCase(publisher, 10, 1);

            // when
            sut.onMessage(orderbook("NVDA"), "B-1");
            sut.onMessage(orderbook("TSLA"), "B-1"); // 호가 큐가 꽉 차 버려짐
            sut.onMessage(trade("NVDA", "959.38"), "A-1");
            sut.onMessage(trade("TSLA", "410.12"), "A-1");

            // then
            List<MarketEvent> drained = new ArrayList<>();
            MarketEvent event = sut.drainOne();
            while (event != null) {
                drained.add(event);
                event = sut.drainOne();
            }
            assertSoftly(softly -> {
                softly.assertThat(drained).hasSize(3);
                softly.assertThat(drained.stream().filter(TradeEvent.class::isInstance))
                        .as("체결은 하나도 안 버려진다")
                        .hasSize(2);
            });
        }

        @Test
        void 체결을_호가보다_먼저_내보낸다() throws Exception {
            // given
            RecordingPublisher publisher = new RecordingPublisher();
            MarketIngestUseCase sut = useCase(publisher, 10, 10);

            // when
            sut.onMessage(orderbook("NVDA"), "B-1");
            sut.onMessage(trade("NVDA", "959.38"), "A-1");

            // then
            assertThat(sut.drainOne()).isInstanceOf(TradeEvent.class);
        }
    }

    @Nested
    @DisplayName("종료할 때")
    class Shutdown {

        @Test
        void 큐에_남은_것을_모두_내보낸다() {
            // given
            RecordingPublisher publisher = new RecordingPublisher();
            MarketIngestUseCase sut = useCase(publisher, 10, 10);
            sut.onMessage(trade("NVDA", "959.38"), "A-1");
            sut.onMessage(orderbook("NVDA"), "B-1");

            // when
            int flushed = sut.flushRemaining();

            // then
            assertSoftly(softly -> {
                softly.assertThat(flushed).isEqualTo(2);
                softly.assertThat(publisher.published).hasSize(2);
                softly.assertThat(publisher.flushes).isEqualTo(1);
                softly.assertThat(sut.queueDepth()).isZero();
            });
        }
    }

    @Nested
    @DisplayName("구독 선언")
    class Declaration {

        @Test
        void 미국장_시간에는_체결과_호가_연결이_각각_생긴다() {
            // given
            MarketIngestUseCase sut = useCase(new RecordingPublisher(), 10, 10);

            // when & then
            assertSoftly(softly -> {
                softly.assertThat(sut.declarationFor("A")).get().asString().contains("trade:us");
                softly.assertThat(sut.declarationFor("B")).get().asString().contains("orderbook:us");
            });
        }

        @Test
        void 한국_창_시간에는_한국을_선언한다() {
            // given  수요일 10:00 KST
            Clock korean = Clock.fixed(Instant.parse("2026-09-16T01:00:00Z"), ZoneOffset.UTC);
            MarketIngestUseCase sut = new MarketIngestUseCase(
                    new RecordingPublisher(),
                    new IngestMetrics(new SimpleMeterRegistry()),
                    korean,
                    "prod-a",
                    Map.of(Market.KR, List.of("005930"), Market.US, List.of("NVDA")),
                    10,
                    10);

            // when & then
            assertThat(sut.declarationFor("A")).get().asString().contains("trade:kr");
        }

        @Test
        void 심볼이_없는_시장이면_선언하지_않는다() {
            // given  미국장 시간인데 미국 심볼이 비어 있음
            MarketIngestUseCase sut = new MarketIngestUseCase(
                    new RecordingPublisher(),
                    new IngestMetrics(new SimpleMeterRegistry()),
                    CLOCK,
                    "prod-a",
                    Map.of(Market.KR, List.of("005930")),
                    10,
                    10);

            // when & then
            assertThat(sut.declarationFor("A")).isEmpty();
        }
    }

    @Nested
    @DisplayName("깨진 메시지")
    class Malformed {

        @Test
        void 큐에_넣지_않고_넘어간다() {
            // given
            MarketIngestUseCase sut = useCase(new RecordingPublisher(), 10, 10);

            // when
            sut.onMessage("{\"type\":\"message\",\"topic\":\"trade:us:NVDA\",\"data\":{}}", "A-1");
            sut.onMessage("깨진 문자열", "A-1");

            // then
            assertThat(sut.queueDepth()).isZero();
        }
    }
}
