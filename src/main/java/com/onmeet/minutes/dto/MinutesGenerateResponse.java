package com.onmeet.minutes.dto;

import java.time.Instant;

public record MinutesGenerateResponse (
    String meetingId,
    String jobId,               // minutes_generation_job_id
    String jobStatus,
    Instant requestedAt
){
}
