package com.onmeet.video.meeting.event;

import com.onmeet.common.dto.event.ChatMessageEvent;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import com.onmeet.video.meeting.event.screenshare.ScreenShareEvent;
import org.springframework.stereotype.Component;

/**
 * TODO: [VIDEO][KAFKA_EVENT] NoOp 대신 실제 KafkaTemplate을 사용하여 메시지를 전송하는 
 * KafkaMeetingEventPublisher 구현이 필요합니다. 
 * ai-service와의 연동을 위해 각 메서드별로 해당 토픽에 이벤트를 발행해야 합니다.
 */
public interface MeetingEventPublisher {

    void publishMeetingStarted(MeetingEvent event);

    void publishMeetingEnded(MeetingEvent event);

    void publishParticipantJoined(ParticipantEvent event);

    void publishParticipantLeft(ParticipantEvent event);

    void publishAudioSegmentReady(AudioChunkReadyEvent event);

    void publishChatMessage(ChatMessageEvent event);

    void publishScreenShareStarted(ScreenShareEvent event);

    void publishScreenShareStopped(ScreenShareEvent event);
}
