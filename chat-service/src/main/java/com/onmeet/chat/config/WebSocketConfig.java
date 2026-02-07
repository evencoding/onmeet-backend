package com.onmeet.chat.config;

import com.onmeet.chat.interceptor.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 설정
 *
 * - /ws-chat: WebSocket 연결 엔드포인트
 * - /app: 클라이언트가 메시지 보낼 때 사용하는 prefix
 * - /topic: 클라이언트가 구독할 때 사용하는 prefix (1:N 브로드캐스트)
 * - /queue: 개인 메시지용 prefix (1:1)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    /**
     * 메시지 브로커 설정
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 구독할 destination prefix
        // 예: /topic/room/123 (채팅방), /queue/user/1 (개인 메시지)
        config.enableSimpleBroker("/topic", "/queue");

        // 클라이언트가 메시지 보낼 때 사용할 prefix
        // 예: /app/chat.send → ChatMessageController의 @MessageMapping("/chat.send")로 라우팅
        config.setApplicationDestinationPrefixes("/app");

        // 개인 메시지용 prefix (선택사항)
        // config.setUserDestinationPrefix("/user");
    }

    /**
     * WebSocket 엔드포인트 등록
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")  // WebSocket 연결 URL
                .setAllowedOriginPatterns("*")  // CORS 허용 (프로덕션에선 제한하세요!)
                .withSockJS();  // SockJS fallback 지원 (WebSocket 미지원 브라우저 대응)

        // SockJS 없이 순수 WebSocket만 사용하려면:
        // registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*");
    }

    /**
     * 클라이언트로부터 받는 메시지에 인터셉터 적용
     * (인증, 로깅 등)
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}