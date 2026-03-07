package com.onmeet.video.meeting.entity.room;

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
@Table(name = "room_tags", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_id", "tag_name"})
})
@EntityListeners(AuditingEntityListener.class)
public class RoomTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id")
    private MeetingRoom room;

    @Column(nullable = false, length = 50)
    private String tagName;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public RoomTag(MeetingRoom room, String tagName) {
        this.room = room;
        this.tagName = tagName;
    }
}
