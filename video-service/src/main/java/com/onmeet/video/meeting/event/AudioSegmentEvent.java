package com.onmeet.video.meeting.event;

public record AudioSegmentEvent(
    String type,
    Long roomId,
    int segmentIndex,
    String s3Path,
    String startTime,
    String endTime
) {
}
