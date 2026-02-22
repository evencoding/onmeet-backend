package com.onmeet.meeting.event;

public interface MeetingEventPublisher {

    void publishMeetingStarted(MeetingEvent event);

    void publishMeetingEnded(MeetingEvent event);

    void publishParticipantJoined(ParticipantEvent event);

    void publishParticipantLeft(ParticipantEvent event);

    void publishAudioSegmentReady(AudioSegmentEvent event);

    void publishChatMessage(ChatMessageEvent event);
}
