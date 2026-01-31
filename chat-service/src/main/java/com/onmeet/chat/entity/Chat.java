package com.onmeet.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "chats",
        indexes = {
                @Index(name = "idx_chats_room_created", columnList = "meet_room_id, created_at"),
                @Index(name = "idx_chats_room_id", columnList = "meet_room_id, id")
        }
)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채팅방/회의방 ID
    @Column(name = "meet_room_id", nullable = false)
    private Long meetRoomId;

    // 유저 서비스의 userId
    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "sender_name", nullable = false, length = 50)
    private String senderName;

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Lob
    @Column(name = "message_content", nullable = false)
    private String messageContent;

    @Lob
    @Column(name = "attachment_json")
    private String attachmentJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ===== 생성 메서드 =====

    public static Chat ofText(Long meetRoomId, Long senderId, String senderName, String senderType, String content) {
        Chat chat = new Chat();
        chat.meetRoomId = meetRoomId;
        chat.senderId = senderId;
        chat.senderName = senderName;
        chat.senderType = senderType;     // "USER"
        chat.messageType = "TEXT";
        chat.messageContent = content;
        chat.attachmentJson = null;
        return chat;
    }

    public static Chat ofAttachment(
            Long meetRoomId,
            Long senderId,
            String senderName,
            String senderType,
            String messageType,            // "IMAGE" | "FILE" | "PDF"
            String caption,
            String attachmentJson
    ) {
        Chat chat = new Chat();
        chat.meetRoomId = meetRoomId;
        chat.senderId = senderId;
        chat.senderName = senderName;
        chat.senderType = senderType;
        chat.messageType = messageType;
        chat.messageContent = (caption == null) ? "" : caption;
        chat.attachmentJson = attachmentJson;
        return chat;
    }

    public static Chat ofSystem(Long meetRoomId, String content) {
        Chat chat = new Chat();
        chat.meetRoomId = meetRoomId;
        chat.senderId = null;
        chat.senderName = "SYSTEM";
        chat.senderType = "SYSTEM";
        chat.messageType = "SYSTEM";
        chat.messageContent = content;
        chat.attachmentJson = null;
        return chat;
    }
}
