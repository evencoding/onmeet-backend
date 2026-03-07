package com.onmeet.video.meeting.event;

import com.onmeet.video.meeting.event.chat.ChatMessageEvent;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.video.meeting.event.recording.AudioSegmentEvent;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import com.onmeet.video.meeting.event.screenshare.ScreenShareEvent;
public interface MeetingEventPublisher {

    void publishMeetingStarted(MeetingEvent event);

    void publishMeetingEnded(MeetingEvent event);

    void publishParticipantJoined(ParticipantEvent event);

    void publishParticipantLeft(ParticipantEvent event);

    void publishAudioSegmentReady(AudioSegmentEvent event);

    void publishChatMessage(ChatMessageEvent event);

    void publishScreenShareStarted(ScreenShareEvent event);

    void publishScreenShareStopped(ScreenShareEvent event);
}
