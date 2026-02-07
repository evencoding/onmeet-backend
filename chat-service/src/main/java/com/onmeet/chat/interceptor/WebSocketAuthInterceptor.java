package com.onmeet.chat.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 연결 시 인증 처리
 *
 * CONNECT 단계에서 JWT 토큰을 검증하고,
 * 세션에 사용자 정보를 저장합니다.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // CONNECT 단계에서 인증 처리
            authenticateUser(accessor);
        }

        return message;
    }

    /**
     * 사용자 인증 및 세션 정보 저장
     */
    private void authenticateUser(StompHeaderAccessor accessor) {
        // Authorization 헤더에서 JWT 토큰 추출
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        log.info("WebSocket CONNECT attempt - Authorization: {}",
                authHeader != null ? "Bearer ***" : "null");

        // TODO: JWT 토큰 파싱 및 검증 로직 구현
        // JwtTokenProvider를 사용해서 토큰 검증
        // Claims claims = jwtTokenProvider.parseToken(token);
        // Long userId = claims.get("userId", Long.class);
        // String userName = claims.get("userName", String.class);

        // 임시: 하드코딩된 사용자 정보 (테스트용)
        Long userId = extractUserIdFromToken(authHeader);
        String userName = extractUserNameFromToken(authHeader);

        // 세션 속성에 사용자 정보 저장
        // 이 정보는 @MessageMapping 메서드에서 SimpMessageHeaderAccessor로 접근 가능
        accessor.getSessionAttributes().put("userId", userId);
        accessor.getSessionAttributes().put("userName", userName);

        log.info("User authenticated - userId: {}, userName: {}, sessionId: {}",
                userId, userName, accessor.getSessionId());
    }

    /**
     * 임시: 토큰에서 userId 추출 (실제로는 JWT 파싱 필요)
     */
    private Long extractUserIdFromToken(String authHeader) {
        // TODO: JWT 파싱 로직으로 교체
        // 지금은 테스트용으로 1L 반환
        return 1L;
    }

    /**
     * 임시: 토큰에서 userName 추출 (실제로는 JWT 파싱 필요)
     */
    private String extractUserNameFromToken(String authHeader) {
        // TODO: JWT 파싱 로직으로 교체
        // 지금은 테스트용으로 "TestUser" 반환
        return "TestUser";
    }

    /**
     * 연결 해제 시 로깅 (선택사항)
     */
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel,
                                    boolean sent, Exception ex) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String userName = (String) accessor.getSessionAttributes().get("userName");
            log.info("User disconnected - userName: {}, sessionId: {}",
                    userName, accessor.getSessionId());
        }
    }
}