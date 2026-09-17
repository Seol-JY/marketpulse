package pro.seol.marketpulse.producer.infrastructure;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.Topics;
import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.Quote;
import pro.seol.marketpulse.common.event.TradeEvent;
import pro.seol.marketpulse.common.serde.EventJson;
import pro.seol.marketpulse.producer.config.MarketEventSerializer;

// Docker 없으면 전체 건너뜀. CI 에서는 항상 실행
@Testcontainers(disabledWithoutDocker = true)
class KafkaMarketEventPublisherTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.1.0");

    private static KafkaMarketEventPublisher publisher() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, MarketEventSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        return new KafkaMarketEventPublisher(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config)));
    }

    private static TradeEvent trade() {
        return new TradeEvent(
                Market.US,
                "NVDA",
                new BigDecimal("959.38"),
                new BigDecimal("4"),
                Instant.parse("2026-09-16T01:25:44.988Z"),
                Instant.parse("2026-09-16T01:25:45.130Z"),
                "prod-a",
                "A-1",
                1L);
    }

    private static OrderbookEvent orderbook() {
        return new OrderbookEvent(
                Market.KR,
                "005930",
                1,
                List.of(new Quote(new BigDecimal("63300"), new BigDecimal("10"))),
                List.of(new Quote(new BigDecimal("63200"), new BigDecimal("11"))),
                Instant.parse("2026-09-16T00:03:12.345Z"),
                Instant.parse("2026-09-16T00:03:12.361Z"),
                "prod-a",
                "B-1",
                2L);
    }

    @Test
    @DisplayName("체결과 호가가 각자 토픽에 키와 함께 들어간다")
    void 체결과_호가가_각자_토픽에_키와_함께_들어간다() {
        // given
        KafkaMarketEventPublisher sut = publisher();

        // when
        sut.publish(trade());
        sut.publish(orderbook());
        sut.flush();

        // then
        ConsumerRecord<String, byte[]> tradeRecord = readOne(Topics.TRADES);
        ConsumerRecord<String, byte[]> orderbookRecord = readOne(Topics.ORDERBOOK);
        MarketEvent restoredTrade = EventJson.fromBytes(tradeRecord.value(), TradeEvent.class);
        MarketEvent restoredOrderbook = EventJson.fromBytes(orderbookRecord.value(), OrderbookEvent.class);
        assertSoftly(softly -> {
            softly.assertThat(tradeRecord.key()).isEqualTo("US:NVDA");
            softly.assertThat(orderbookRecord.key()).isEqualTo("KR:005930");
            softly.assertThat(restoredTrade.seq()).isEqualTo(1L);
            softly.assertThat(((TradeEvent) restoredTrade).price()).isEqualByComparingTo("959.38");
            softly.assertThat(((OrderbookEvent) restoredOrderbook).bestBid().price())
                    .isEqualByComparingTo("63200");
        });
    }

    private ConsumerRecord<String, byte[]> readOne(final String topic) {
        final Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            final long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                final var records = consumer.poll(Duration.ofSeconds(2));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }
        throw new AssertionError("30초 안에 " + topic + " 메시지를 받지 못함");
    }
}
