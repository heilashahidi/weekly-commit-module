package com.weeklycommit.lifecycle.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} whose instant can be set and advanced, for deterministic
 * deadline tests. The lifecycle deadline-backstop logic reads time from an
 * injected Clock (see ClockConfig); tests swap in this clock and move it past a
 * deadline to assert auto-transitions without real waiting.
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneId.of("UTC"));
    }

    public MutableClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    public void setInstant(Instant next) {
        this.instant = next;
    }

    public void advance(Duration by) {
        this.instant = this.instant.plus(by);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId targetZone) {
        return new MutableClock(instant, targetZone);
    }
}
