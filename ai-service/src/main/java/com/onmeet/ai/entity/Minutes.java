package com.onmeet.ai.entity;

import com.onmeet.ai.enums.MinutesAccessScope;
import com.onmeet.ai.enums.MinutesStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "minutes")
public class Minutes {

    @Id
    @Column(name = "meeting_id", length = 64, nullable = false)
    private String meetingId;

    @Column(name = "transcript_id", length = 64, nullable = false)
    private String transcriptId;

    @Column(name = "transcript_s3_key", length = 512, nullable = false)
    private String transcriptS3Key;

    @Column(name = "summary_s3_key", length = 512)
    private String summaryS3Key;

    @Lob
    @Column(name = "summary_json", columnDefinition = "LONGTEXT", nullable = false)
    private String summaryJson;

    @Lob
    @Column(name = "user_edited_summary_json", columnDefinition = "LONGTEXT")
    private String userEditedSummaryJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private MinutesStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", length = 16, nullable = false)
    private MinutesAccessScope accessScope;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Minutes(String meetingId,
                    String transcriptId,
                    String transcriptS3Key,
                    String summaryS3Key,
                    String summaryJson) {
        this.meetingId = meetingId;
        this.transcriptId = transcriptId;
        this.transcriptS3Key = transcriptS3Key;
        this.summaryS3Key = summaryS3Key;
        this.summaryJson = summaryJson;
        this.userEditedSummaryJson = null;
        this.status = MinutesStatus.GENERATED;
        this.accessScope = MinutesAccessScope.PRIVATE; // 기본값
        this.lastError = null;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Minutes createGenerated(String meetingId,
                                          String transcriptId,
                                          String transcriptS3Key,
                                          String summaryS3Key,
                                          String summaryJson) {
        return new Minutes(meetingId, transcriptId, transcriptS3Key, summaryS3Key, summaryJson);
    }

    public void applyGenerated(String transcriptId,
                               String transcriptS3Key,
                               String summaryS3Key,
                               String summaryJson) {
        this.transcriptId = transcriptId;
        this.transcriptS3Key = transcriptS3Key;
        this.summaryS3Key = summaryS3Key;
        this.summaryJson = summaryJson;
        this.status = MinutesStatus.GENERATED;
        this.lastError = null;
        touch();
    }

    public void markFailed(String reason) {
        this.status = MinutesStatus.FAILED;
        this.lastError = reason;
        touch();
    }

    public void updateAccessScope(MinutesAccessScope scope) {
        this.accessScope = scope;
        touch();
    }

    public void applyUserEdit(String userEditedSummaryJson) {
        this.userEditedSummaryJson = userEditedSummaryJson;
        this.status = MinutesStatus.EDITED_BY_USER;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
