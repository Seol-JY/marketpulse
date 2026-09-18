package pro.seol.marketpulse.producer.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import pro.seol.marketpulse.common.Market;

// 연결 2개 한도라 두 시장을 동시에 구독할 수 없음
// 공휴일 미반영, 휴장일엔 데이터가 없어 구독만 유휴
public final class MarketCalendar {

    private static final ZoneId KR_ZONE = ZoneId.of("Asia/Seoul");
    // 통합 시세(KRX+NXT) 프리 개시부터 애프터 종료까지, 세션 사이 공백도 한국으로 유지
    private static final LocalTime KR_OPEN = LocalTime.of(8, 0);
    private static final LocalTime KR_CLOSE = LocalTime.of(20, 0);

    private MarketCalendar() {}

    // 미국 전 세션이 한국 창의 여집합이라 서머타임 보정이 필요 없음
    public static Market marketAt(final Instant instant) {
        return isKoreanWindow(instant) ? Market.KR : Market.US;
    }

    private static boolean isKoreanWindow(final Instant instant) {
        final ZonedDateTime local = instant.atZone(KR_ZONE);
        if (local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        final LocalTime time = local.toLocalTime();
        return !time.isBefore(KR_OPEN) && time.isBefore(KR_CLOSE);
    }
}
