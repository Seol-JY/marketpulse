package pro.seol.marketpulse.producer.config;

import org.apache.kafka.common.serialization.Serializer;

import pro.seol.marketpulse.common.event.MarketEvent;
import pro.seol.marketpulse.common.serde.EventJson;

// common 직렬화 재사용. 컨슈머와 같은 규칙 유지
public class MarketEventSerializer implements Serializer<MarketEvent> {

    @Override
    public byte[] serialize(final String topic, final MarketEvent data) {
        return data == null ? null : EventJson.toBytes(data);
    }
}
