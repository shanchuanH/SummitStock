package com.example.portfolio.market.provider;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

@Component
public final class UsEquityTradingCalendar implements TradingCalendar {
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime CLOSE = LocalTime.of(16, 0);

    @Override
    public LocalDate latestCompletedSession(Instant now) {
        var marketNow = now.atZone(MARKET_ZONE);
        var candidate = marketNow.toLocalDate();
        if (!isSession(candidate) || marketNow.toLocalTime().isBefore(CLOSE)) candidate = candidate.minusDays(1);
        while (!isSession(candidate)) candidate = candidate.minusDays(1);
        return candidate;
    }

    @Override
    public boolean isSession(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !isObservedNewYear(date)
                && !date.equals(nthWeekday(date.getYear(), Month.JANUARY, DayOfWeek.MONDAY, 3))
                && !date.equals(nthWeekday(date.getYear(), Month.FEBRUARY, DayOfWeek.MONDAY, 3))
                && !date.equals(lastWeekday(date.getYear(), Month.MAY, DayOfWeek.MONDAY))
                && !isObserved(date, Month.JUNE, 19)
                && !isObserved(date, Month.JULY, 4)
                && !date.equals(nthWeekday(date.getYear(), Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                && !date.equals(nthWeekday(date.getYear(), Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                && !isObserved(date, Month.DECEMBER, 25);
    }

    private static boolean isObservedNewYear(LocalDate date) {
        return isObserved(date, Month.JANUARY, 1)
                || date.equals(observed(LocalDate.of(date.getYear() + 1, Month.JANUARY, 1)));
    }

    private static boolean isObserved(LocalDate date, Month month, int day) {
        return date.equals(observed(LocalDate.of(date.getYear(), month, day)));
    }

    private static LocalDate observed(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private static LocalDate nthWeekday(int year, Month month, DayOfWeek day, int occurrence) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(occurrence, day));
    }

    private static LocalDate lastWeekday(int year, Month month, DayOfWeek day) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(day));
    }
}
