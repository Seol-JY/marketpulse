package pro.seol.marketpulse.producer.application;

public interface AccessTokenProvider {

    String token();

    // 토스는 계정당 토큰 하나만 유효. 다른 곳에서 발급하면 기존 토큰이 죽음
    void invalidate();
}
