package pro.seol.marketpulse.producer.application;

import pro.seol.marketpulse.common.event.MarketEvent;

public interface MarketEventPublisher {

    void publish(MarketEvent event);

    void flush();
}
