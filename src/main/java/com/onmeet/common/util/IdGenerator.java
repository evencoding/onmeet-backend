package com.onmeet.common.util;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    public UUID newUuid() {
        return UUID.randomUUID();
    }
}
