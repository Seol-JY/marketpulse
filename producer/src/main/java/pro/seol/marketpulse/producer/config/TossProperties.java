package pro.seol.marketpulse.producer.config;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import pro.seol.marketpulse.common.Market;

@Validated
@ConfigurationProperties(prefix = "toss")
public record TossProperties(
        @NotBlank String baseUrl,
        @NotBlank String wsUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        // 국내·미국 티커 체계가 달라 시장별 분리
        @NotEmpty Map<Market, List<String>> symbols) {}
