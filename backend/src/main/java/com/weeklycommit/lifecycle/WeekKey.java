package com.weeklycommit.lifecycle;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;

/**
 * Pure helpers for the org-wide ISO-8601 week key (KTD 7), e.g. {@code 2026-W23}.
 * The week is a fixed org-wide cycle (origin Dependencies/Assumptions), so the key
 * is derived entirely from a calendar date — no per-user state. Used here (U4
 * get-or-create the current week's plan) and by carry-forward (U8 next-week seed).
 *
 * <p>Computation reads {@link IsoFields#WEEK_BASED_YEAR} and
 * {@link IsoFields#WEEK_OF_WEEK_BASED_YEAR} directly rather than a
 * {@code DateTimeFormatter} pattern, because the {@code Y}/{@code w} pattern letters
 * are <em>locale-sensitive</em> (a US-default locale starts weeks on Sunday and
 * would mis-key the year boundary). The {@code IsoFields} are always ISO-8601, so
 * the boundary where, e.g., 2021-01-01 belongs to {@code 2020-W53} is handled
 * correctly. The key is always zero-padded to two week digits
 * ({@code W01}..{@code W53}).
 */
public final class WeekKey {

    private WeekKey() {}

    /** The ISO week key for "now" read from the injected {@link Clock}. */
    public static String current(Clock clock) {
        return of(LocalDate.now(clock));
    }

    /** The ISO week key containing {@code date}. */
    public static String of(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }

    /**
     * The week key one ISO week after {@code weekKey}, handling year boundaries
     * (e.g. {@code 2026-W52 -> 2027-W01}, {@code 2026-W53} style overflow). Parses
     * the key back to the Monday of that ISO week, adds 7 days, reformats.
     */
    public static String nextWeek(String weekKey) {
        return of(mondayOf(weekKey).plusWeeks(1));
    }

    /** The Monday (ISO first day) of the week identified by {@code weekKey}. */
    private static LocalDate mondayOf(String weekKey) {
        int year = Integer.parseInt(weekKey.substring(0, 4));
        int week = Integer.parseInt(weekKey.substring(6));
        // Start from a date guaranteed to be in the target week-based year, then
        // pin to the requested ISO week and its Monday. Jan 4 is always in ISO
        // week 1 of its week-based year, giving a stable anchor for the .with()s.
        return LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
            .with(WeekFields.ISO.dayOfWeek(), 1);
    }
}
