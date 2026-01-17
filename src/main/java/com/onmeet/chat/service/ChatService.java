package com.onmeet.chat.service;

import com.onmeet.chat.dto.ChatCreateRequest;
import com.onmeet.chat.dto.ChatResponse;
import com.onmeet.chat.entity.Chat;
import com.onmeet.chat.entity.MessageType;
import com.onmeet.chat.entity.SenderType;
import com.onmeet.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;

    @Transactional
    public ChatResponse save(Long meetRoomId, ChatCreateRequest req) {

        Chat chat = Chat.create(
                meetRoomId,
                req.senderName(),
                req.senderType(),
                req.messageContent(),
                MessageType.TEXT
        );

        Chat saved = chatRepository.save(chat);
        return ChatResponse.from(saved);
    }

    @Transactional
    public ChatResponse saveNotice(
            Long meetRoomId,
            SenderType senderType,
            String message
    ) {
        Chat chat = Chat.create(
                meetRoomId,
                "알림",
                senderType,          // USER or GUEST
                message,
                MessageType.NOTICE
        );
        return ChatResponse.from(chatRepository.save(chat));
    }

    public List<ChatResponse> getLatest(Long meetRoomId, int limit) {
        int size = clampLimit(limit);
        var pageable = PageRequest.of(0, size);

        return chatRepository
                .findByMeetRoomIdOrderByCreatedAtDesc(meetRoomId, pageable)
                .stream()
                .map(ChatResponse::from)
                .toList();
    }

    public List<ChatResponse> getBefore(Long meetRoomId, Instant before, int limit) {
        int size = clampLimit(limit);
        var pageable = PageRequest.of(0, size);

        return chatRepository
                .findByMeetRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(meetRoomId, before, pageable)
                .stream()
                .map(ChatResponse::from)
                .toList();
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return 50;
        return Math.min(limit, 100);
    }
}
