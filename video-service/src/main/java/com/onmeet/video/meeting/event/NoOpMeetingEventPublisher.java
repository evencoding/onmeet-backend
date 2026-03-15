package com.onmeet.video.meeting.event;

import com.onmeet.video.meeting.event.chat.ChatMessageEvent;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.video.meeting.event.recording.AudioSegmentEvent;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import com.onmeet.video.meeting.event.screenshare.ScreenShareEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// CHECK [video-담당자]: NoOp은 기본 활성화(kafka.enabled 미설정 or false 시 사용)
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMeetingEventPublisher implements MeetingEventPublisher {

    /** 
     * TODO: [VIDEO][KAFKA_EVENT] 실제 운영 환경에서는 이 NoOp 구현체 대신 
     * KafkaTemplate을 사용하는 구현체를 생성하고 @Primary로 등록하여 사용해야 합니다. 
     */

    private static final Logger log = LoggerFactory.getLogger(NoOpMeetingEventPublisher.class);

    @Override
    public void publishMeetingStarted(MeetingEvent event) {
        log.debug("Meeting started event: roomId={}, hostUserId={}", event.roomId(), event.hostUserId());
    }

    @Override
    public void publishMeetingEnded(MeetingEvent event) {
        log.debug("Meeting ended event: roomId={}, participantCount={}", event.roomId(), event.participantCount());
    }

    @Override
    public void publishParticipantJoined(ParticipantEvent event) {
        log.debug("Participant joined event: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishParticipantLeft(ParticipantEvent event) {
        log.debug("Participant left event: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishAudioSegmentReady(AudioSegmentEvent event) {
        log.debug("Audio segment ready event: roomId={}, segmentIndex={}", event.roomId(), event.segmentIndex());
    }

    @Override
    public void publishChatMessage(ChatMessageEvent event) {
        log.debug("Chat message event: roomId={}, senderId={}, type={}", event.roomId(), event.senderId(), event.messageType());
    }

    @Override
    public void publishScreenShareStarted(ScreenShareEvent event) {
        log.debug("Screen share started event: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishScreenShareStopped(ScreenShareEvent event) {
        log.debug("Screen share stopped event: roomId={}, userId={}", event.roomId(), event.userId());
    }
}
