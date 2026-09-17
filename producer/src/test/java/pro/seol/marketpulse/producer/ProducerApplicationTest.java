package pro.seol.marketpulse.producer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import pro.seol.marketpulse.producer.application.MarketEventPublisher;
import pro.seol.marketpulse.producer.application.MarketIngestUseCase;

@SpringBootTest(
        properties = {
            "producer.enabled=false",
            "toss.client-id=test",
            "toss.client-secret=test",
            "toss.symbols.US=NVDA,TSLA",
            "toss.symbols.KR=005930",
            "spring.kafka.bootstrap-servers=localhost:19092"
        })
class ProducerApplicationTest {

    private final ApplicationContext context;

    ProducerApplicationTest(final ApplicationContext context) {
        this.context = context;
    }

    @Test
    @DisplayName("애플리케이션 컨텍스트가 뜬다")
    void 애플리케이션_컨텍스트가_뜬다() {
        assertThat(context.getBean(MarketIngestUseCase.class)).isNotNull();
    }

    @Test
    @DisplayName("나가는 쪽 인터페이스에 구현이 꽂힌다")
    void 나가는_쪽_인터페이스에_구현이_꽂힌다() {
        assertThat(context.getBean(MarketEventPublisher.class).getClass().getPackageName())
                .isEqualTo("pro.seol.marketpulse.producer.infrastructure");
    }
}
