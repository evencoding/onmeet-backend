package com.onmeet.chat.controller;

import com.onmeet.chat.dto.ChatMessageResponseDto;
import com.onmeet.chat.dto.ChatSendRequestDto;
import com.onmeet.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload @Valid ChatSendRequestDto request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        SessionUser sessionUser = resolveSessionUser(headerAccessor);

        log.info("[WebSocket] Message received - roomId: {}, sender: {}, type: {}, content: {}",
                request.meetRoomId(), sessionUser.senderName(), request.messageType(),
                truncate(request.content(), 50));

        ChatMessageResponseDto response = chatService.sendMessage(
                request,
                sessionUser.senderId(),
                sessionUser.senderName(),
                sessionUser.senderType()
        );

        String destination = "/topic/room/" + response.meetRoomId();
        messagingTemplate.convertAndSend(destination, response);

        log.info("[WebSocket] Message saved and broadcast - id: {}, dest: {}",
                response.id(), destination);
    }

    @MessageMapping("/chat.join")
    public void joinRoom(@Payload JoinRoomRequestDto request, SimpMessageHeaderAccessor headerAccessor) {
        SessionUser sessionUser = resolveSessionUser(headerAccessor);

        ChatMessageResponseDto msg = new ChatMessageResponseDto(
                null,
                request.meetRoomId(),
                null,
                "SYSTEM",
                "SYSTEM",
                "SYSTEM",
                sessionUser.senderName() + "님이 입장하셨습니다.",
                null,
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/room/" + request.meetRoomId(), msg);
    }

    @MessageMapping("/chat.leave")
    public void leaveRoom(@Payload LeaveRoomRequestDto request, SimpMessageHeaderAccessor headerAccessor) {
        SessionUser sessionUser = resolveSessionUser(headerAccessor);

        ChatMessageResponseDto msg = new ChatMessageResponseDto(
                null,
                request.meetRoomId(),
                null,
                "SYSTEM",
                "SYSTEM",
                "SYSTEM",
                sessionUser.senderName() + "님이 퇴장하셨습니다.",
                null,
                LocalDateTime.now()
        );

        messagingTemplate.convertAndSend("/topic/room/" + request.meetRoomId(), msg);
    }

    private SessionUser resolveSessionUser(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) {
            // 테스트/개발용 기본값
            return new SessionUser(1L, "TEST_USER", "USER");
        }

        Object uid = attrs.get("userId");
        Object uname = attrs.get("userName");
        Object utype = attrs.get("senderType");

        Long senderId = (uid instanceof Number) ? ((Number) uid).longValue() : 1L;
        String senderName = (uname != null) ? uname.toString() : "TEST_USER";
        String senderType = (utype != null) ? utype.toString() : "USER";

        return new SessionUser(senderId, senderName, senderType);
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "null";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    private record SessionUser(Long senderId, String senderName, String senderType) {}
}

record JoinRoomRequestDto(Long meetRoomId) {}
record LeaveRoomRequestDto(Long meetRoomId) {}
