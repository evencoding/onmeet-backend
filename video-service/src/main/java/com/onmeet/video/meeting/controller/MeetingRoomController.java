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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Meeting Room", description = "회의방 CRUD 및 운영 API")
@RestController
@RequestMapping("/api/rooms")
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;

    public MeetingRoomController(MeetingRoomService meetingRoomService) {
        this.meetingRoomService = meetingRoomService;
    }

    @Operation(summary = "회의방 생성")
    @PostMapping
    public ApiResponse<MeetingRoomResponse> create(@Valid @RequestBody RoomCreateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.create(request, userId));
    }

    @Operation(summary = "회의방 목록 조회")
    @GetMapping
    public ApiResponse<Page<MeetingRoomResponse>> list(@RequestParam(required = false) RoomStatus status,
                                                       @RequestParam(required = false) RoomType type,
                                                       @RequestParam(required = false) RoomAccessScope accessScope,
                                                       @RequestParam(required = false) Long hostUserId,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.list(status, type, accessScope, hostUserId, pageable));
    }

    @Operation(summary = "회의방 상세 조회")
    @GetMapping("/{roomId}")
    public ApiResponse<MeetingRoomDetailResponse> get(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.get(roomId));
    }

    @Operation(summary = "회의방 정보 수정")
    @PatchMapping("/{roomId}")
    public ApiResponse<MeetingRoomResponse> update(@PathVariable Long roomId,
                                                   @RequestBody RoomUpdateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.update(roomId, request, userId));
    }

    @Operation(summary = "회의방 삭제")
    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> delete(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.delete(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "방 코드로 회의방 조회")
    @GetMapping("/code/{roomCode}")
    public ApiResponse<MeetingRoomResponse> findByCode(@PathVariable String roomCode) {
        return ApiResponse.ok(meetingRoomService.findByCode(roomCode));
    }

    @Operation(summary = "방 코드 재생성")
    @PostMapping("/{roomId}/regenerate-code")
    public ApiResponse<MeetingRoomResponse> regenerateCode(@PathVariable Long roomId,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.regenerateCode(roomId, userId));
    }

    @Operation(summary = "회의방 참가")
    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomJoinResponse> join(@PathVariable Long roomId,
                                              @RequestBody(required = false) RoomJoinRequest request,
                                              @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.join(roomId, request, userId));
    }

    @Operation(summary = "회의방 퇴장")
    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leave(@PathVariable Long roomId,
                                   @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.leave(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의 시작")
    @PostMapping("/{roomId}/start")
    public ApiResponse<MeetingRoomResponse> start(@PathVariable Long roomId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.start(roomId, userId));
    }

    @Operation(summary = "회의 종료")
    @PostMapping("/{roomId}/end")
    public ApiResponse<MeetingRoomResponse> end(@PathVariable Long roomId,
                                                @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.end(roomId, userId));
    }

    @Operation(summary = "회의방 잠금")
    @PostMapping("/{roomId}/lock")
    public ApiResponse<Void> lock(@PathVariable Long roomId,
                                  @RequestBody(required = false) RoomLockRequest request,
                                  @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.lock(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 잠금 해제")
    @PostMapping("/{roomId}/unlock")
    public ApiResponse<Void> unlock(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.unlock(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 설정 조회")
    @GetMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> getSettings(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getSettings(roomId));
    }

    @Operation(summary = "회의방 설정 수정")
    @PatchMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> updateSettings(@PathVariable Long roomId,
                                                            @RequestBody RoomSettingsUpdateRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.updateSettings(roomId, request, userId));
    }

    @Operation(summary = "회의방 태그 추가")
    @PostMapping("/{roomId}/tags")
    public ApiResponse<Void> addTag(@PathVariable Long roomId,
                                    @Valid @RequestBody TagCreateRequest request,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addTag(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 태그 삭제")
    @DeleteMapping("/{roomId}/tags/{tagName}")
    public ApiResponse<Void> removeTag(@PathVariable Long roomId,
                                       @PathVariable String tagName,
                                       @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeTag(roomId, tagName, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "태그로 회의방 검색")
    @GetMapping("/tags/{tagName}")
    public ApiResponse<List<MeetingRoomResponse>> searchByTag(@PathVariable String tagName) {
        return ApiResponse.ok(meetingRoomService.searchByTag(tagName));
    }

    @Operation(summary = "회의방 즐겨찾기 추가")
    @PostMapping("/{roomId}/favorite")
    public ApiResponse<Void> addFavorite(@PathVariable Long roomId,
                                         @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 즐겨찾기 삭제")
    @DeleteMapping("/{roomId}/favorite")
    public ApiResponse<Void> removeFavorite(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "즐겨찾기 회의방 목록 조회")
    @GetMapping("/favorites")
    public ApiResponse<List<MeetingRoomResponse>> listFavorites(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listFavorites(userId));
    }

    @Operation(summary = "회의 예약 생성")
    @PostMapping("/schedule")
    public ApiResponse<MeetingRoomResponse> schedule(@Valid @RequestBody RoomScheduleRequest request,
                                                     @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.schedule(request, userId));
    }

    @Operation(summary = "예약된 회의 목록 조회")
    @GetMapping("/scheduled")
    public ApiResponse<List<MeetingRoomResponse>> listScheduled(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listScheduled(userId));
    }

    @Operation(summary = "회의 예약 일정 변경")
    @PatchMapping("/{roomId}/schedule")
    public ApiResponse<MeetingRoomResponse> updateSchedule(@PathVariable Long roomId,
                                                           @RequestParam Instant scheduledAt,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.updateSchedule(roomId, scheduledAt, userId));
    }

    @Operation(summary = "회의 예약 취소")
    @DeleteMapping("/{roomId}/schedule")
    public ApiResponse<Void> cancelSchedule(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.cancelSchedule(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의 리마인더 발송")
    @PostMapping("/{roomId}/schedule/remind")
    public ApiResponse<Void> sendReminder(@PathVariable Long roomId,
                                          @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.sendReminder(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가했던 회의 이력 조회")
    @GetMapping("/history")
    public ApiResponse<List<MeetingRoomResponse>> listHistory(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.listHistory(userId));
    }

    @Operation(summary = "월별 회의 통계 조회")
    @GetMapping("/history/monthly")
    public ApiResponse<List<MonthlyStatsResponse>> getMonthlyStats(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.getMonthlyStats(userId));
    }

    @Operation(summary = "회의방 통계 조회")
    @GetMapping("/{roomId}/stats")
    public ApiResponse<RoomStatsResponse> getStats(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getStats(roomId));
    }

    @Operation(summary = "회의방 타임라인 조회")
    @GetMapping("/{roomId}/timeline")
    public ApiResponse<List<TimelineEntry>> getTimeline(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getTimeline(roomId));
    }
}
