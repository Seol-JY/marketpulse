package pro.seol.marketpulse.producer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingest")
public record IngestProperties(
        @NotBlank String producerId,
        @Min(1000) int tradeQueueSize,
        @Min(1000) int orderbookQueueSize) {}
