package com.onmeet.video.common.util;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ClockProvider {

    public Instant now() {
        return Instant.now();
    }
}
