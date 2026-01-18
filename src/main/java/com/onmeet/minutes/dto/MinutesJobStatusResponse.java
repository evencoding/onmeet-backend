package com.onmeet.minutes.dto;

import java.time.Instant;

public record MinutesJobStatusResponse(
        String meetingId,
        String jobId,           // minutes_generation_job_id
        String jobStatus,       // PENDING/PROCESSING/DONE/FAILED/NOT_STARTED
        String failureReason,
        Instant requestedAt,
        Instant completedAt
) {
}
