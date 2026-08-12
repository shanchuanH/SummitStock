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
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);

    @Override
    public LocalDate latestCompletedSession(Instant now) {
        var marketNow = now.atZone(MARKET_ZONE);
        var candidate = marketNow.toLocalDate();
        if (!isSession(candidate) || now.isBefore(sessionClose(candidate))) candidate = candidate.minusDays(1);
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
                && !date.equals(easterSunday(date.getYear()).minusDays(2))
                && !date.equals(lastWeekday(date.getYear(), Month.MAY, DayOfWeek.MONDAY))
                && !isObserved(date, Month.JUNE, 19)
                && !isObserved(date, Month.JULY, 4)
                && !date.equals(nthWeekday(date.getYear(), Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                && !date.equals(nthWeekday(date.getYear(), Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                && !isObserved(date, Month.DECEMBER, 25);
    }

    @Override
    public Instant sessionOpen(LocalDate date) {
        requireSession(date);
        return date.atTime(OPEN).atZone(MARKET_ZONE).toInstant();
    }

    @Override
    public Instant sessionClose(LocalDate date) {
        requireSession(date);
        return date.atTime(isEarlyClose(date) ? EARLY_CLOSE : REGULAR_CLOSE)
                .atZone(MARKET_ZONE)
                .toInstant();
    }

    @Override
    public boolean isEarlyClose(LocalDate date) {
        if (!isSession(date)) return false;
        var thanksgiving = nthWeekday(date.getYear(), Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
        if (date.equals(thanksgiving.plusDays(1))) return true;
        if (date.getMonth() == Month.DECEMBER && date.getDayOfMonth() == 24) return true;
        return date.getMonth() == Month.JULY && date.getDayOfMonth() == 3;
    }

    private void requireSession(LocalDate date) {
        if (!isSession(date)) throw new IllegalArgumentException("Date is not an XNYS/XNAS trading session: " + date);
    }

    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
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
