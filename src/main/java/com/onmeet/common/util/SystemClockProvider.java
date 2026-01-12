package com.onmeet.common.util;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClockProvider implements ClockProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
