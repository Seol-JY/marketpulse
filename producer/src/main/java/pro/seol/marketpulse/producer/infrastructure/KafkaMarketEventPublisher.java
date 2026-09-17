package pro.seol.marketpulse.producer.infrastructure;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import pro.seol.marketpulse.common.Topics;
import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.event.OrderbookEvent;
import pro.seol.marketpulse.common.event.TradeEvent;
import pro.seol.marketpulse.producer.application.MarketEventPublisher;

@Slf4j
@Component
class KafkaMarketEventPublisher implements MarketEventPublisher {

    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, MarketEvent> kafkaTemplate;

    KafkaMarketEventPublisher(final KafkaTemplate<String, MarketEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(final MarketEvent event) {
        kafkaTemplate.send(topicOf(event), event.partitionKey(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("전송 실패 topic={} key={} seq={}", topicOf(event), event.partitionKey(), event.seq(), error);
            }
        });
    }

    @Override
    public void flush() {
        kafkaTemplate.flush();
        kafkaTemplate.destroy();
        log.info("Kafka 버퍼 비움 timeout={}s", FLUSH_TIMEOUT.toSeconds());
    }

    private String topicOf(final MarketEvent event) {
        return switch (event) {
            case TradeEvent ignored -> Topics.TRADES;
            case OrderbookEvent ignored -> Topics.ORDERBOOK;
        };
    }
}
