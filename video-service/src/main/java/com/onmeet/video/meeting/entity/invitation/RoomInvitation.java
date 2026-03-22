package com.onmeet.video.meeting.entity.invitation;

import com.onmeet.video.meeting.entity.room.MeetingRoom;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_invitations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_id", "invitee_user_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class RoomInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id")
    private MeetingRoom room;

    @Column(nullable = false)
    private Long inviterUserId;

    @Column(nullable = false)
    private Long inviteeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public RoomInvitation(MeetingRoom room, Long inviterUserId, Long inviteeUserId) {
        this.room = room;
        this.inviterUserId = inviterUserId;
        this.inviteeUserId = inviteeUserId;
        this.status = InvitationStatus.PENDING;
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
    }

    public void decline() {
        this.status = InvitationStatus.DECLINED;
    }

    public void cancel() {
        this.status = InvitationStatus.CANCELLED;
    }

    public boolean isPending() {
        return this.status == InvitationStatus.PENDING;
    }
}
