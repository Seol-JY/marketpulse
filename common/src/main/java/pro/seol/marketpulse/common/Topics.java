package pro.seol.marketpulse.common;

// 토픽 정의는 seol-gitops 의 infra/kafka/topics.yaml 이 단일 출처
public final class Topics {

    public static final String TRADES = "market.trades.v1";
    public static final String ORDERBOOK = "market.orderbook.v1";
    public static final String CANDLES_1M = "market.candles.1m.v1";
    public static final String ALERTS = "alerts.raised.v1";

    private Topics() {}
}
