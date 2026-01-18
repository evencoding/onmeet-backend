package com.onmeet.minutes.entity;

import com.onmeet.meeting.entity.Meeting;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "minutes_generation_jobs")
@EntityListeners(AuditingEntityListener.class)
public class MinutesGenerationJob {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MinutesJobStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public MinutesGenerationJob(Meeting meeting) {
        this.meeting = meeting;
        this.status = MinutesJobStatus.PENDING;
    }

    public void markProcessing() {
        this.status = MinutesJobStatus.PROCESSING;
    }

    public void markDone() {
        this.status = MinutesJobStatus.DONE;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = MinutesJobStatus.FAILED;
        this.failureReason = reason;
    }
}
