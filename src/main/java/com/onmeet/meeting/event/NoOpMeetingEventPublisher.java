package com.onmeet.meeting.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpMeetingEventPublisher implements MeetingEventPublisher {

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
}
