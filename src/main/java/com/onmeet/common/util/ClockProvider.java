package com.onmeet.common.util;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}
