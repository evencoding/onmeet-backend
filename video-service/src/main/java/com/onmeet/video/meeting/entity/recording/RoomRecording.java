package com.onmeet.video.meeting.entity.recording;

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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_recordings")
@EntityListeners(AuditingEntityListener.class)
public class RoomRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id")
    private MeetingRoom room;

    @Column(nullable = false, length = 100)
    private String egressId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordingStatus status;

    @Column(length = 500)
    private String s3Path;

    private Long fileSizeBytes;

    private Integer durationSeconds;

    private Integer segmentIndex;

    private Instant startedAt;

    private Instant endedAt;

    @Column(length = 100)
    private String participantIdentity;

    @Column(length = 100)
    private String trackSid;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public RoomRecording(MeetingRoom room, String egressId, RecordingType type, Instant startedAt) {
        this.room = room;
        this.egressId = egressId;
        this.type = type;
        this.status = RecordingStatus.RECORDING;
        this.startedAt = startedAt;
    }

    public RoomRecording(MeetingRoom room, String egressId, RecordingType type, Instant startedAt,
                         String participantIdentity, String trackSid) {
        this(room, egressId, type, startedAt);
        this.participantIdentity = participantIdentity;
        this.trackSid = trackSid;
    }

    public void markProcessing() {
        this.status = RecordingStatus.PROCESSING;
    }

    public void markCompleted(String s3Path, Long fileSizeBytes, Instant endedAt) {
        this.status = RecordingStatus.COMPLETED;
        this.s3Path = s3Path;
        this.fileSizeBytes = fileSizeBytes;
        this.endedAt = endedAt;
        if (this.startedAt != null) {
            this.durationSeconds = (int) Duration.between(this.startedAt, endedAt).getSeconds();
        }
    }

    public void markFailed(String errorMessage, Instant endedAt) {
        this.status = RecordingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.endedAt = endedAt;
    }

    public void assignSegment(int segmentIndex, String s3Path) {
        this.segmentIndex = segmentIndex;
        this.s3Path = s3Path;
    }

    public boolean isRecording() {
        return this.status == RecordingStatus.RECORDING;
    }
}
