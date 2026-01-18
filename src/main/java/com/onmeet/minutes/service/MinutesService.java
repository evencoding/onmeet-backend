package com.onmeet.minutes.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.meeting.entity.Meeting;
import com.onmeet.meeting.repository.MeetingRepository;
import com.onmeet.minutes.dto.*;
import com.onmeet.minutes.entity.Minutes;
import com.onmeet.minutes.entity.MinutesGenerationJob;
import com.onmeet.minutes.entity.MinutesStatus;
import com.onmeet.minutes.repository.MinutesRepository;
import com.onmeet.minutes.repository.MinutesGenerationJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final MeetingRepository meetingRepository;
    private final MinutesGenerationJobRepository jobRepository;

    public MinutesService(MinutesRepository minutesRepository, MeetingRepository meetingRepository, MinutesGenerationJobRepository jobRepository) {
        this.minutesRepository = minutesRepository;
        this.meetingRepository = meetingRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public MinutesResponse getMinutesByMeeting(String meetingId) {
        Minutes minutes = minutesRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("minutes not found"));

        return new MinutesResponse(
                minutes.getId(),
                String.valueOf(minutes.getMeeting().getId()),
                minutes.getStatus().name(),
                minutes.getSummaryText(),
                minutes.getCreatedAt(),
                minutes.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public MinutesJobStatusResponse getJobStatusByMeetingId(String meetingId) {
        return jobRepository.findFirstByMeeting_IdOrderByRequestedAtDesc(meetingId)
                .map(job -> new MinutesJobStatusResponse(
                        meetingId,
                        job.getId(),
                        job.getStatus().name(),
                        job.getFailureReason(),
                        job.getRequestedAt(),
                        job.getCompletedAt()
                ))
                .orElse(new MinutesJobStatusResponse(
                        meetingId,
                        null,
                        "NOT_STARTED",
                        null,
                        null,
                        null
                ));
    }

    @Transactional
    public MinutesGenerateResponse generate(String meetingId, MinutesGenerateRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("meeting not found"));

        MinutesGenerationJob job = new MinutesGenerationJob(meeting); // status=PENDING
        jobRepository.save(job);

        runMinutesPipelineAsync(job.getId(), request);

        return new MinutesGenerateResponse(
                meetingId,
                job.getId(),
                job.getStatus().name(),
                job.getRequestedAt()
        );
    }

    @Transactional
    public MinutesGenerateResponse retry(String meetingId) {
        // retry도 generate와 동일하게 새 job 생성(이력 남김)
        return generate(meetingId, new MinutesGenerateRequest(null, null));
    }

    @Async
    @Transactional
    public void runMinutesPipelineAsync(String jobId, MinutesGenerateRequest request) {
        MinutesGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("job not found"));

        job.markProcessing();
        jobRepository.save(job);

        try {
            String meetingId = String.valueOf(job.getMeeting().getId());

            // TODO 1) voice_segments -> transcript 구성
            // TODO 2) 외부 NLP API 호출 (request.language/style)

            String summary = "TODO: external NLP summary result";

            Minutes minutes = minutesRepository.findByMeetingId(meetingId)
                    .orElse(new Minutes(job.getMeeting(), MinutesStatus.DRAFT, null));

            minutes.updateSummary(summary); // Minutes에 메서드 추가(추천)
            minutesRepository.save(minutes);

            job.markDone();
            jobRepository.save(job);
        } catch (Exception e) {
            job.markFailed(e.getMessage());
            jobRepository.save(job);
        }
    }
}
