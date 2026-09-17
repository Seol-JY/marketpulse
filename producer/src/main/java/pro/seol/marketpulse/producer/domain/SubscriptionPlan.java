package pro.seol.marketpulse.producer.domain;

import java.util.List;
import java.util.Map;

import pro.seol.marketpulse.common.Market;
import pro.seol.marketpulse.common.serde.EventJson;

// 연결을 채널로 분리. 호가 지연이 체결에 역압을 주지 않게 함
public record SubscriptionPlan(String connectionId, Channel channel, Market market, List<String> symbols) {

    public static final int MAX_SYMBOLS_PER_CONNECTION = 100;

    public SubscriptionPlan {
        symbols =
                List.copyOf(symbols).stream().limit(MAX_SYMBOLS_PER_CONNECTION).toList();
    }

    public static List<SubscriptionPlan> forMarket(final Market market, final List<String> symbols) {
        return List.of(
                new SubscriptionPlan("A", Channel.TRADE, market, symbols),
                new SubscriptionPlan("B", Channel.ORDERBOOK, market, symbols));
    }

    // 토스는 JSON 배열을 요구하고 선언마다 전체를 교체함
    public String declaration() {
        return EventJson.mapper()
                .writeValueAsString(List.of(
                        Map.of("id", connectionId),
                        Map.of("type", channel.getCode() + ":" + market.name().toLowerCase(), "codes", symbols)));
    }
}
