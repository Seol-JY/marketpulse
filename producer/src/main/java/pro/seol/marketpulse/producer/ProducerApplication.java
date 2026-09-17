package pro.seol.marketpulse.producer;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import pro.seol.marketpulse.producer.application.IngestMetrics;
import pro.seol.marketpulse.producer.application.MarketEventPublisher;
import pro.seol.marketpulse.producer.application.MarketIngestUseCase;
import pro.seol.marketpulse.producer.config.IngestProperties;
import pro.seol.marketpulse.producer.config.TossProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProducerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IngestMetrics ingestMetrics(final MeterRegistry registry) {
        return new IngestMetrics(registry);
    }

    @Bean
    MarketIngestUseCase marketIngestUseCase(
            final MarketEventPublisher publisher,
            final IngestMetrics metrics,
            final Clock clock,
            final IngestProperties ingest,
            final TossProperties toss) {
        return new MarketIngestUseCase(
                publisher,
                metrics,
                clock,
                ingest.producerId(),
                toss.symbols(),
                ingest.tradeQueueSize(),
                ingest.orderbookQueueSize());
    }
}
