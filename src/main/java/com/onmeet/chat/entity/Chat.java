package com.onmeet.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "chats",
        indexes = {
                @Index(name="idx_chats_meetroom_createdat", columnList="meet_room_id, created_at")
        }
)

public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meet_room_id", nullable = false)
    private UUID meetRoomId;

    @Column(nullable = false)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SenderType senderType;

    @Lob
    @Column(nullable = false)
    private String messageContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType messageType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Chat create(
            UUID meetRoomId,
            String senderName,
            SenderType senderType,
            String messageContent,
            MessageType messageType
    ) {
        Chat chat = new Chat();
        chat.meetRoomId = meetRoomId;
        chat.senderName = senderName;
        chat.senderType = senderType;
        chat.messageContent = messageContent;
        chat.messageType = messageType;
        return chat;
    }

}
