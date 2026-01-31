package com.onmeet.chat.service;

import com.onmeet.chat.dto.ChatHistoryResponseDto;
import com.onmeet.chat.dto.ChatMessageResponseDto;
import com.onmeet.chat.dto.ChatSendRequestDto;
import com.onmeet.chat.entity.Chat;
import com.onmeet.chat.repository.ChatRepository;
import com.onmeet.chat.dto.ChatSendRequestValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatSendRequestValidator validator; // 없으면 이 필드/생성자/validate 호출 제거

    public ChatService(ChatRepository chatRepository, ChatSendRequestValidator validator) {
        this.chatRepository = chatRepository;
        this.validator = validator;
    }

    @Transactional
    public ChatMessageResponseDto sendMessage(
            ChatSendRequestDto req,
            Long senderId,
            String senderName,
            String senderType
    ) {
        if (validator != null) {
            validator.validate(req);
        }

        String type = normalize(req.messageType());

        Chat saved;
        if ("TEXT".equals(type)) {
            saved = chatRepository.save(
                    Chat.ofText(req.meetRoomId(), senderId, senderName, senderType, req.content())
            );
        } else {
            // IMAGE/FILE/PDF
            saved = chatRepository.save(
                    Chat.ofAttachment(
                            req.meetRoomId(),
                            senderId,
                            senderName,
                            senderType,
                            type,
                            req.content(),          // caption 용도(없으면 null/"" 가능)
                            req.attachmentJson()
                    )
            );
        }

        return toResponse(saved);
    }

    public ChatHistoryResponseDto getHistory(Long meetRoomId, Long beforeId, int size) {
        int pageSize = Math.max(1, Math.min(size, 200)); // 방어
        int fetchSize = pageSize + 1;

        List<Chat> fetched;
        if (beforeId == null) {
            fetched = chatRepository.findLatestByRoomId(meetRoomId, PageRequest.of(0, fetchSize));
        } else {
            fetched = chatRepository.findBeforeIdByRoomId(meetRoomId, beforeId, PageRequest.of(0, fetchSize));
        }

        boolean hasMore = fetched.size() > pageSize;
        if (hasMore) {
            // size+1로 가져온 마지막 1개 제거
            fetched = fetched.subList(0, pageSize);
        }

        Collections.reverse(fetched);

        List<ChatMessageResponseDto> messages = new ArrayList<>(fetched.size());
        Long minId = null; // nextBeforeId 계산용 (이번 chunk에서 가장 작은 id)

        for (Chat c : fetched) {
            messages.add(toResponse(c));
            if (c.getId() != null) {
                minId = (minId == null) ? c.getId() : Math.min(minId, c.getId());
            }
        }

        Long nextBeforeId = hasMore ? minId : null;

        return new ChatHistoryResponseDto(
                meetRoomId,
                messages,
                nextBeforeId,
                hasMore
        );
    }

    private ChatMessageResponseDto toResponse(Chat c) {
        return new ChatMessageResponseDto(
                c.getId(),
                c.getMeetRoomId(),
                c.getSenderId(),
                c.getSenderName(),
                c.getSenderType(),
                c.getMessageType(),
                c.getMessageContent(),
                c.getAttachmentJson(),
                c.getCreatedAt()
        );
    }

    private String normalize(String messageType) {
        return (messageType == null) ? "" : messageType.trim().toUpperCase();
    }


}
