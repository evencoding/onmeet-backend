package com.onmeet.ai.entity;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "transcript_id", length = 64, nullable = false)
    private String transcriptId;

    @Column(name = "transcript_s3_key", length = 512, nullable = false)
    private String transcriptS3Key;

    @Column(name = "summary_s3_key", length = 512)
    private String summaryS3Key;

    @Lob
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Lob
    @Column(name = "keywords", columnDefinition = "LONGTEXT")
    private String keywords;

    @Lob
    @Column(name = "decisions", columnDefinition = "LONGTEXT")
    private String decisions;

    @Lob
    @Column(name = "action_items", columnDefinition = "LONGTEXT")
    private String actionItems;

    @Lob
    @Column(name = "summary_json", columnDefinition = "LONGTEXT", nullable = false)
    private String summaryJson;

    @Lob
    @Column(name = "user_edited_summary_json", columnDefinition = "LONGTEXT")
    private String userEditedSummaryJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private MinutesStatus status;



    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Minutes(Long roomId,
                    String transcriptId,
                    String transcriptS3Key,
                    String summaryS3Key,
                    String description,
                    String keywords,
                    String decisions,
                    String actionItems,
                    String summaryJson) {
        this.roomId = roomId;
        this.transcriptId = transcriptId;
        this.transcriptS3Key = transcriptS3Key;
        this.summaryS3Key = summaryS3Key;
        this.description = description;
        this.keywords = keywords;
        this.decisions = decisions;
        this.actionItems = actionItems;
        this.summaryJson = summaryJson;
        this.userEditedSummaryJson = null;
        this.status = MinutesStatus.GENERATED;
        this.lastError = null;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Minutes createGenerated(Long roomId,
                                          String transcriptId,
                                          String transcriptS3Key,
                                          String summaryS3Key,
                                          String description,
                                          String keywords,
                                          String decisions,
                                          String actionItems,
                                          String summaryJson) {
        return new Minutes(roomId, transcriptId, transcriptS3Key, summaryS3Key, description, keywords, decisions, actionItems, summaryJson);
    }

    public void applyGenerated(String transcriptId,
                               String transcriptS3Key,
                               String summaryS3Key,
                               String description,
                               String keywords,
                               String decisions,
                               String actionItems,
                               String summaryJson) {
        this.transcriptId = transcriptId;
        this.transcriptS3Key = transcriptS3Key;
        this.summaryS3Key = summaryS3Key;
        this.description = description;
        this.keywords = keywords;
        this.decisions = decisions;
        this.actionItems = actionItems;
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



    public void applyUserEdit(String userEditedSummaryJson, String description, String keywords, String decisions, String actionItems) {
        this.userEditedSummaryJson = userEditedSummaryJson;
        if (description != null) this.description = description;
        if (keywords != null) this.keywords = keywords;
        if (decisions != null) this.decisions = decisions;
        if (actionItems != null) this.actionItems = actionItems;
        this.status = MinutesStatus.EDITED_BY_USER;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
