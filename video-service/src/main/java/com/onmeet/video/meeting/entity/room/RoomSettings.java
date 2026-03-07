package com.onmeet.video.meeting.entity.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
@Table(name = "room_settings")
@EntityListeners(AuditingEntityListener.class)
public class RoomSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "room_id", unique = true)
    private MeetingRoom room;

    @Column(nullable = false)
    private boolean videoEnabled;

    @Column(nullable = false)
    private boolean audioEnabled;

    @Column(nullable = false)
    private boolean screenShareAllowed;

    @Column(nullable = false)
    private boolean chatEnabled;

    @Column(nullable = false)
    private boolean recordingEnabled;

    @Column(nullable = false)
    private boolean waitingRoom;

    @Column(nullable = false)
    private boolean autoMuteOnJoin;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public static RoomSettings createDefault(MeetingRoom room) {
        RoomSettings settings = new RoomSettings();
        settings.room = room;
        settings.videoEnabled = true;
        settings.audioEnabled = true;
        settings.screenShareAllowed = true;
        settings.chatEnabled = true;
        settings.recordingEnabled = true;
        settings.waitingRoom = false;
        settings.autoMuteOnJoin = true;
        return settings;
    }

    public void update(Boolean videoEnabled, Boolean audioEnabled, Boolean screenShareAllowed,
                       Boolean chatEnabled, Boolean recordingEnabled, Boolean waitingRoom,
                       Boolean autoMuteOnJoin) {
        if (videoEnabled != null) this.videoEnabled = videoEnabled;
        if (audioEnabled != null) this.audioEnabled = audioEnabled;
        if (screenShareAllowed != null) this.screenShareAllowed = screenShareAllowed;
        if (chatEnabled != null) this.chatEnabled = chatEnabled;
        if (recordingEnabled != null) this.recordingEnabled = recordingEnabled;
        if (waitingRoom != null) this.waitingRoom = waitingRoom;
        if (autoMuteOnJoin != null) this.autoMuteOnJoin = autoMuteOnJoin;
    }
}
