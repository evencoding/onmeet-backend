package com.onmeet.chat.entity;

import com.onmeet.chat.enums.MessageType;
import com.onmeet.chat.enums.SenderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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
    private Long meetRoomId;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SenderType senderType;

    @Lob
    @Column(nullable = false)
    private String messageContent;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
