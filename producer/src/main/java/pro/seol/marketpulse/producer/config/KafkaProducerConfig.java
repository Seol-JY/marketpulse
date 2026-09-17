package pro.seol.marketpulse.producer.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import pro.seol.marketpulse.common.event.MarketEvent;

@Configuration
class KafkaProducerConfig {

    @Bean
    ProducerFactory<String, MarketEvent> marketEventProducerFactory(final KafkaProperties kafkaProperties) {
        final Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, MarketEventSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 64L * 1024 * 1024);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, MarketEvent> marketEventKafkaTemplate(final ProducerFactory<String, MarketEvent> factory) {
        return new KafkaTemplate<>(factory);
    }
}
