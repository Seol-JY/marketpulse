package pro.seol.marketpulse.producer.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import pro.seol.marketpulse.common.Market;

// 공휴일 미반영. 휴장일엔 데이터가 없어 구독만 유휴
public final class MarketCalendar {

    private static final ZoneId KR_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId US_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime KR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime KR_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime US_OPEN = LocalTime.of(9, 30);
    private static final LocalTime US_CLOSE = LocalTime.of(16, 0);

    private MarketCalendar() {}

    public static Optional<Market> openAt(final Instant instant) {
        if (isOpen(instant, KR_ZONE, KR_OPEN, KR_CLOSE)) {
            return Optional.of(Market.KR);
        }
        if (isOpen(instant, US_ZONE, US_OPEN, US_CLOSE)) {
            return Optional.of(Market.US);
        }
        return Optional.empty();
    }

    private static boolean isOpen(
            final Instant instant, final ZoneId zone, final LocalTime open, final LocalTime close) {
        final ZonedDateTime local = instant.atZone(zone);
        if (local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        final LocalTime time = local.toLocalTime();
        return !time.isBefore(open) && time.isBefore(close);
    }
}
