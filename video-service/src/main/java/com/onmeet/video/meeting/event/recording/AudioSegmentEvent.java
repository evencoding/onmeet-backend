package com.onmeet.video.meeting.event.recording;

public record AudioSegmentEvent(
    String type,
    Long roomId,
    String participantIdentity,
    int segmentIndex,
    String s3Path,
    String startTime,
    String endTime
) {
}
