package com.onmeet.notification.sse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Long sseTimeoutMillis;

    public SseEmitterManager(Long sseTimeoutMillis) {
        this.sseTimeoutMillis = sseTimeoutMillis;
    }

    public SseEmitter add(Long userId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((ex) -> emitters.remove(userId));
        return emitter;
    }

    public void send(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("notification").data(data));
        } catch (IOException ex) {
            log.debug("SSE send failed", ex);
            emitters.remove(userId);
        }
    }
}
