package com.onmeet.meeting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_favorites", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "room_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class RoomFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id")
    private MeetingRoom room;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public RoomFavorite(Long userId, MeetingRoom room) {
        this.userId = userId;
        this.room = room;
    }
}
