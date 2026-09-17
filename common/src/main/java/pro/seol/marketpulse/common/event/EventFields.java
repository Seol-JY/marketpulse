package pro.seol.marketpulse.common.event;

import java.math.BigDecimal;
import java.time.Instant;

import pro.seol.marketpulse.common.exception.ExceptionCode;
import pro.seol.marketpulse.common.exception.PipelineException;

final class EventFields {

    private EventFields() {}

    static void requireSymbol(final String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw PipelineException.from(ExceptionCode.INVALID_SYMBOL);
        }
    }

    static void requirePositive(final BigDecimal value, final ExceptionCode exceptionCode) {
        if (value == null || value.signum() <= 0) {
            throw PipelineException.from(exceptionCode);
        }
    }

    static void requireSequence(final long seq) {
        if (seq <= 0) {
            throw PipelineException.from(ExceptionCode.INVALID_SEQUENCE);
        }
    }

    static void requireTimestamps(final Instant... timestamps) {
        for (final Instant timestamp : timestamps) {
            if (timestamp == null) {
                throw PipelineException.from(ExceptionCode.MISSING_TIMESTAMP);
            }
        }
    }
}
