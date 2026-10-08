package io.github.abrar118.matbank.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock that can be moved by hand, used to replay months of demo history. */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant now;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
