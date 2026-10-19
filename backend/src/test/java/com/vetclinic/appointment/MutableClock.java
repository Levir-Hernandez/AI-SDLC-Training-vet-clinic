package com.vetclinic.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose time can be moved forward. */
class MutableClock extends Clock {

    private Instant instant;

    MutableClock(LocalDateTime now) {
        this.instant = now.toInstant(ZoneOffset.UTC);
    }

    void advance(Duration d) {
        instant = instant.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
