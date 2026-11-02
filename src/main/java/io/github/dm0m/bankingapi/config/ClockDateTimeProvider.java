package io.github.dm0m.bankingapi.config;

import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

public class ClockDateTimeProvider implements DateTimeProvider {

    private final Clock clock;

    public ClockDateTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(this.clock.instant());
    }
}
