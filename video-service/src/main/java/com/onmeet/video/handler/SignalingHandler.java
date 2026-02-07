package com.onmeet.video.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);

    // Thread-safe session storage
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.info("New WebSocket Connection: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        logger.info("WebSocket Disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Broadcast signal to all other sessions (simple signaling)
        // In a real app, you'd target specific peers based on room ID
        String payload = message.getPayload();
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen() && !s.getId().equals(session.getId())) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    logger.error("Failed to send message to session {}: {}", s.getId(), e.getMessage());
                }
            }
        }
    }
}
