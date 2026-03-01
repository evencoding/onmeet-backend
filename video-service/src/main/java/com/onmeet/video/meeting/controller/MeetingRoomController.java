package com.onmeet.video.meeting.controller;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.RoomCreateRequest;
import com.onmeet.video.meeting.dto.RoomJoinRequest;
import com.onmeet.video.meeting.dto.RoomJoinResponse;
import com.onmeet.video.meeting.dto.MonthlyStatsResponse;
import com.onmeet.video.meeting.dto.RoomLockRequest;
import com.onmeet.video.meeting.dto.RoomScheduleRequest;
import com.onmeet.video.meeting.dto.RoomSettingsResponse;
import com.onmeet.video.meeting.dto.RoomSettingsUpdateRequest;
import com.onmeet.video.meeting.dto.RoomStatsResponse;
import com.onmeet.video.meeting.dto.RoomUpdateRequest;
import com.onmeet.video.meeting.dto.TagCreateRequest;
import com.onmeet.video.meeting.dto.TimelineEntry;
import com.onmeet.video.meeting.entity.RoomAccessScope;
import com.onmeet.video.meeting.entity.RoomStatus;
import com.onmeet.video.meeting.entity.RoomType;
import com.onmeet.video.meeting.service.MeetingRoomService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;

    public MeetingRoomController(MeetingRoomService meetingRoomService) {
        this.meetingRoomService = meetingRoomService;
    }

    @PostMapping
    public ApiResponse<MeetingRoomResponse> create(@Valid @RequestBody RoomCreateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.create(request, userId));
    }

    @GetMapping
    public ApiResponse<Page<MeetingRoomResponse>> list(@RequestParam(required = false) RoomStatus status,
                                                       @RequestParam(required = false) RoomType type,
                                                       @RequestParam(required = false) RoomAccessScope accessScope,
                                                       @RequestParam(required = false) Long hostUserId,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.list(status, type, accessScope, hostUserId, pageable));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<MeetingRoomDetailResponse> get(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.get(roomId));
    }

    @PatchMapping("/{roomId}")
    public ApiResponse<MeetingRoomResponse> update(@PathVariable Long roomId,
                                                   @RequestBody RoomUpdateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.update(roomId, request, userId));
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> delete(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.delete(roomId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/code/{roomCode}")
    public ApiResponse<MeetingRoomResponse> findByCode(@PathVariable String roomCode) {
        return ApiResponse.ok(meetingRoomService.findByCode(roomCode));
    }

    @PostMapping("/{roomId}/regenerate-code")
    public ApiResponse<MeetingRoomResponse> regenerateCode(@PathVariable Long roomId,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.regenerateCode(roomId, userId));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomJoinResponse> join(@PathVariable Long roomId,
                                              @RequestBody(required = false) RoomJoinRequest request,
                                              @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.join(roomId, request, userId));
    }

    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leave(@PathVariable Long roomId,
                                   @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.leave(roomId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<MeetingRoomResponse> start(@PathVariable Long roomId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.start(roomId, userId));
    }

    @PostMapping("/{roomId}/end")
    public ApiResponse<MeetingRoomResponse> end(@PathVariable Long roomId,
                                                @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.end(roomId, userId));
    }

    @PostMapping("/{roomId}/lock")
    public ApiResponse<Void> lock(@PathVariable Long roomId,
                                  @RequestBody(required = false) RoomLockRequest request,
                                  @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.lock(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{roomId}/unlock")
    public ApiResponse<Void> unlock(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.unlock(roomId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> getSettings(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getSettings(roomId));
    }

    @PatchMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> updateSettings(@PathVariable Long roomId,
                                                            @RequestBody RoomSettingsUpdateRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.updateSettings(roomId, request, userId));
    }

    @PostMapping("/{roomId}/tags")
    public ApiResponse<Void> addTag(@PathVariable Long roomId,
                                    @Valid @RequestBody TagCreateRequest request,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addTag(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{roomId}/tags/{tagName}")
    public ApiResponse<Void> removeTag(@PathVariable Long roomId,
                                       @PathVariable String tagName,
                                       @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeTag(roomId, tagName, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tags/{tagName}")
    public ApiResponse<List<MeetingRoomResponse>> searchByTag(@PathVariable String tagName) {
        return ApiResponse.ok(meetingRoomService.searchByTag(tagName));
    }

    @PostMapping("/{roomId}/favorite")
    public ApiResponse<Void> addFavorite(@PathVariable Long roomId,
                                         @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{roomId}/favorite")
    public ApiResponse<Void> removeFavorite(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/favorites")
    public ApiResponse<List<MeetingRoomResponse>> listFavorites(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listFavorites(userId));
    }

    @PostMapping("/schedule")
    public ApiResponse<MeetingRoomResponse> schedule(@Valid @RequestBody RoomScheduleRequest request,
                                                     @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.schedule(request, userId));
    }

    @GetMapping("/scheduled")
    public ApiResponse<List<MeetingRoomResponse>> listScheduled(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listScheduled(userId));
    }

    @PatchMapping("/{roomId}/schedule")
    public ApiResponse<MeetingRoomResponse> updateSchedule(@PathVariable Long roomId,
                                                           @RequestParam Instant scheduledAt,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.updateSchedule(roomId, scheduledAt, userId));
    }

    @DeleteMapping("/{roomId}/schedule")
    public ApiResponse<Void> cancelSchedule(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.cancelSchedule(roomId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{roomId}/schedule/remind")
    public ApiResponse<Void> sendReminder(@PathVariable Long roomId,
                                          @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.sendReminder(roomId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/history")
    public ApiResponse<List<MeetingRoomResponse>> listHistory(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listHistory(userId));
    }

    @GetMapping("/history/monthly")
    public ApiResponse<List<MonthlyStatsResponse>> getMonthlyStats(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.getMonthlyStats(userId));
    }

    @GetMapping("/{roomId}/stats")
    public ApiResponse<RoomStatsResponse> getStats(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getStats(roomId));
    }

    @GetMapping("/{roomId}/timeline")
    public ApiResponse<List<TimelineEntry>> getTimeline(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getTimeline(roomId));
    }
}
