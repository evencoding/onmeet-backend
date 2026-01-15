package com.onmeet.meeting.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class MeetingCreateRequest {
    private String title;
    private String description;
    private String meetTag;
    private String teamId;
    private String hostId;
    private List<String> invitedUserIds;

    // 날짜와 시간을 따로 받아 서버에서 합침
    private LocalDate date;
    private LocalTime time;
}
