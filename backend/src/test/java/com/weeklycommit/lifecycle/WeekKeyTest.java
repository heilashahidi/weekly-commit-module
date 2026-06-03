package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.lifecycle.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage of the ISO week-key helper. Proves the key is ISO-8601 (not
 * locale-sensitive), that {@code current} reads the injected clock, and that
 * {@code nextWeek} increments correctly across both a normal week and the
 * year boundary (including a real 53-week year).
 */
class WeekKeyTest {

    @Test
    void ofKnownDateGivesExpectedIsoWeek() {
        // 2026-06-03 is a Wednesday in ISO week 23.
        assertThat(WeekKey.of(LocalDate.of(2026, 6, 3))).isEqualTo("2026-W23");
    }

    @Test
    void ofUsesIsoWeekBasedYearAtBoundary() {
        // ISO-8601: 2021-01-01 (Friday) belongs to week 53 of week-based-year 2020,
        // which a locale-default formatter would get wrong.
        assertThat(WeekKey.of(LocalDate.of(2021, 1, 1))).isEqualTo("2020-W53");
    }

    @Test
    void currentReadsTheInjectedClock() {
        // 2026-06-03T12:00:00Z is in ISO week 23.
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"), ZoneOffset.UTC);
        assertThat(WeekKey.current(clock)).isEqualTo("2026-W23");
    }

    @Test
    void nextWeekIncrementsWithinAYear() {
        assertThat(WeekKey.nextWeek("2026-W23")).isEqualTo("2026-W24");
    }

    @Test
    void nextWeekCrossesYearBoundaryFrom52() {
        // 2026 is a 53-week ISO year, so W52 -> W53 stays in 2026.
        assertThat(WeekKey.nextWeek("2026-W52")).isEqualTo("2026-W53");
    }

    @Test
    void nextWeekFromLastIsoWeekRollsToNextYear() {
        // ...and W53 rolls over to the next week-based year.
        assertThat(WeekKey.nextWeek("2026-W53")).isEqualTo("2027-W01");
    }

    @Test
    void nextWeekFrom52RollsToNextYearWhenYearHas52Weeks() {
        // 2025 is a 52-week ISO year, so W52 rolls straight to 2026-W01.
        assertThat(WeekKey.nextWeek("2025-W52")).isEqualTo("2026-W01");
    }
}
