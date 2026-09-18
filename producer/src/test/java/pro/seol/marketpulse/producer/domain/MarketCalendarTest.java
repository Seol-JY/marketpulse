package pro.seol.marketpulse.producer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pro.seol.marketpulse.common.Market;

class MarketCalendarTest {

    private static final ZoneId KR_ZONE = ZoneId.of("Asia/Seoul");

    private static Instant kst(final String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(KR_ZONE).toInstant();
    }

    @Nested
    @DisplayName("한국 창")
    class KoreanWindow {

        @Test
        void 프리마켓_개시_시각부터_한국이다() {
            // given 2026-09-18 은 금요일
            Instant open = kst("2026-09-18T08:00");
            Instant justBefore = kst("2026-09-18T07:59");

            // when & then
            assertSoftly(softly -> {
                softly.assertThat(MarketCalendar.marketAt(open)).isEqualTo(Market.KR);
                softly.assertThat(MarketCalendar.marketAt(justBefore)).isEqualTo(Market.US);
            });
        }

        @Test
        void 애프터마켓_종료_시각은_미국으로_넘어간다() {
            // given
            Instant lastMinute = kst("2026-09-18T19:59");
            Instant close = kst("2026-09-18T20:00");

            // when & then
            assertSoftly(softly -> {
                softly.assertThat(MarketCalendar.marketAt(lastMinute)).isEqualTo(Market.KR);
                softly.assertThat(MarketCalendar.marketAt(close)).isEqualTo(Market.US);
            });
        }

        @Test
        void 정규_마감과_애프터_개시_사이_공백도_한국으로_유지한다() {
            assertThat(MarketCalendar.marketAt(kst("2026-09-18T15:35"))).isEqualTo(Market.KR);
        }

        @Test
        void 주말은_한국_시간대여도_미국이다() {
            // given 2026-09-19 는 토요일
            Instant saturdayNoon = kst("2026-09-19T12:00");

            // when & then
            assertThat(MarketCalendar.marketAt(saturdayNoon)).isEqualTo(Market.US);
        }
    }

    @Nested
    @DisplayName("미국 창")
    class UnitedStatesWindow {

        @Test
        void 토요일_새벽은_미국_금요일_애프터마켓이라_미국이다() {
            assertThat(MarketCalendar.marketAt(kst("2026-09-19T06:00"))).isEqualTo(Market.US);
        }

        @Test
        void 서머타임_유무와_무관하게_정규장이_미국_창에_들어온다() {
            // given
            Instant summerRegular = kst("2026-07-15T23:00"); // EDT 10:00
            Instant winterRegular = kst("2026-01-15T00:00"); // EST 10:00

            assertSoftly(softly -> {
                softly.assertThat(MarketCalendar.marketAt(summerRegular)).isEqualTo(Market.US);
                softly.assertThat(MarketCalendar.marketAt(winterRegular)).isEqualTo(Market.US);
            });
        }

        @Test
        void 월요일_새벽은_한국_개장_전이라_미국이다() {
            assertThat(MarketCalendar.marketAt(kst("2026-09-21T03:00"))).isEqualTo(Market.US);
        }
    }
}
