package com.onmeet.video.meeting.controller.room;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.room.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.room.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.room.RoomCreateRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinResponse;
import com.onmeet.video.meeting.dto.room.MonthlyStatsResponse;
import com.onmeet.video.meeting.dto.room.RoomLockRequest;
import com.onmeet.video.meeting.dto.room.RoomScheduleRequest;
import com.onmeet.video.meeting.dto.room.RoomSettingsResponse;
import com.onmeet.video.meeting.dto.room.RoomSettingsUpdateRequest;
import com.onmeet.video.meeting.dto.room.RoomStatsResponse;
import com.onmeet.video.meeting.dto.room.RoomUpdateRequest;
import com.onmeet.video.meeting.dto.room.TagCreateRequest;
import com.onmeet.video.meeting.dto.room.TimelineEntry;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.service.room.MeetingRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
// CHECK [video-담당자]: URL 패턴 /api/rooms → /v1/rooms 변경 (gateway /video/v1/** 라우팅 통일)
@RequestMapping("/v1/rooms")
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;

    public MeetingRoomController(MeetingRoomService meetingRoomService) {
        this.meetingRoomService = meetingRoomService;
    }

    @Operation(summary = "회의방 생성", description = "새 회의방을 생성합니다. TEAM 접근 범위 설정 시 teamId가 필수이며, 호스트는 해당 팀의 멤버여야 합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의방 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "TEAM 접근 범위 설정 시 teamId 누락",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_001\",\"status\":400,\"message\":\"TEAM 접근 범위 설정 시 teamId는 필수입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 해당 팀의 멤버가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_003\",\"status\":403,\"message\":\"호스트는 해당 팀의 멤버여야 합니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 팀 ID",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_002\",\"status\":404,\"message\":\"존재하지 않는 팀입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "해당 시간대에 이미 예약된 회의가 있는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_022\",\"status\":409,\"message\":\"해당 시간대에 이미 예약된 회의가 있습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping
    public ApiResponse<MeetingRoomResponse> create(@Valid @RequestBody RoomCreateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.create(request, userId));
    }

    @Operation(summary = "회의방 목록 조회", description = "상태, 타입, 접근 범위, 호스트 등의 조건으로 회의방 목록을 페이지네이션 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공")
    })
    @GetMapping
    public ApiResponse<Page<MeetingRoomResponse>> list(@RequestParam(required = false) RoomStatus status,
                                                       @RequestParam(required = false) RoomType type,
                                                       @RequestParam(required = false) RoomAccessScope accessScope,
                                                       @RequestParam(required = false) Long hostUserId,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.list(status, type, accessScope, hostUserId, pageable));
    }

    @Operation(summary = "회의방 상세 조회", description = "roomId로 회의방 상세 정보를 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 roomId로 요청한 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/{roomId}")
    public ApiResponse<MeetingRoomDetailResponse> get(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.get(roomId));
    }

    @Operation(summary = "회의방 정보 수정", description = "회의방의 이름, 설명 등 기본 정보를 수정합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 수정 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PatchMapping("/{roomId}")
    public ApiResponse<MeetingRoomResponse> update(@PathVariable Long roomId,
                                                   @RequestBody RoomUpdateRequest request,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.update(roomId, request, userId));
    }

    @Operation(summary = "회의방 삭제", description = "회의방을 삭제합니다. 진행 중인 회의실은 삭제할 수 없습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "진행 중인 회의실은 삭제 불가",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_005\",\"status\":400,\"message\":\"진행 중인 회의실은 삭제할 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 삭제 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> delete(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.delete(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "방 코드로 회의방 조회", description = "고유 입장 코드로 회의방을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "해당 코드의 회의실이 존재하지 않는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_006\",\"status\":404,\"message\":\"해당 코드의 회의실을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        )
    })
    // CHECK [video-담당자]: findByCode 반환 타입 MeetingRoomResponse -> MeetingRoomDetailResponse
    @GetMapping("/code/{roomCode}")
    public ApiResponse<MeetingRoomDetailResponse> findByCode(@PathVariable String roomCode) {
        return ApiResponse.ok(meetingRoomService.findByCode(roomCode));
    }

    @Operation(summary = "방 코드 재생성", description = "회의방의 입장 코드를 새로 발급합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "코드 재생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 재생성 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/regenerate-code")
    public ApiResponse<MeetingRoomResponse> regenerateCode(@PathVariable Long roomId,
                                                           @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.regenerateCode(roomId, userId));
    }

    @Operation(summary = "회의방 참가", description = "회의방에 참가하고 LiveKit 접속 토큰을 발급받습니다. 비밀번호가 설정된 경우 필요합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참가 성공 - LiveKit 토큰 반환"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "참가 불가 - 이미 종료된 회의실이거나 최대 인원 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "alreadyEnded", summary = "이미 종료된 회의실", value = "{\"code\":\"VIDEO_007\",\"status\":400,\"message\":\"이미 종료된 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "roomFull", summary = "최대 인원 초과", value = "{\"code\":\"VIDEO_011\",\"status\":400,\"message\":\"회의실 최대 참가자 수에 도달했습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "접근 불가 - 팀 멤버 전용이거나 비밀번호 불일치",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "notTeamMember", summary = "팀 멤버 전용 회의실", value = "{\"code\":\"VIDEO_009\",\"status\":403,\"message\":\"팀 멤버만 입장할 수 있는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "wrongPassword", summary = "비밀번호 불일치", value = "{\"code\":\"VIDEO_010\",\"status\":403,\"message\":\"회의실 비밀번호가 올바르지 않습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 해당 회의실에 입장 중인 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_008\",\"status\":409,\"message\":\"이미 입장한 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomJoinResponse> join(@PathVariable Long roomId,
                                              @RequestBody(required = false) RoomJoinRequest request,
                                              @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.join(roomId, request, userId));
    }

    @Operation(summary = "회의방 퇴장", description = "현재 참가 중인 회의방에서 퇴장합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "퇴장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 참가자 기록을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "notParticipant", summary = "참가자 기록 없음", value = "{\"code\":\"VIDEO_012\",\"status\":404,\"message\":\"해당 회의실의 참가자가 아닙니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leave(@PathVariable Long roomId,
                                   @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.leave(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의 시작", description = "WAITING 상태의 회의방을 ACTIVE로 전환합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의 시작 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "WAITING 상태가 아닌 회의실을 시작하려는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_013\",\"status\":400,\"message\":\"WAITING 상태의 회의실만 시작할 수 있습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 시작 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/start")
    public ApiResponse<MeetingRoomResponse> start(@PathVariable Long roomId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.start(roomId, userId));
    }

    @Operation(summary = "회의 종료", description = "ACTIVE 상태의 회의방을 ENDED로 전환합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회의 종료 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "진행 중이 아닌 회의실을 종료하려는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_014\",\"status\":400,\"message\":\"진행 중인 회의실만 종료할 수 있습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 종료 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/end")
    public ApiResponse<MeetingRoomResponse> end(@PathVariable Long roomId,
                                                @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.end(roomId, userId));
    }

    @Operation(summary = "회의방 잠금", description = "새 참가자의 입장을 차단합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "잠금 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/lock")
    public ApiResponse<Void> lock(@PathVariable Long roomId,
                                  @RequestBody(required = false) RoomLockRequest request,
                                  @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.lock(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 잠금 해제", description = "잠긴 회의방을 해제합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "잠금 해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/unlock")
    public ApiResponse<Void> unlock(@PathVariable Long roomId,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.unlock(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 설정 조회", description = "회의방의 녹화 허용, 화면 공유 허용 등 세부 설정을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설정 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 설정 정보를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "settingsNotFound", summary = "설정 정보 없음", value = "{\"code\":\"VIDEO_017\",\"status\":404,\"message\":\"회의실 설정 정보를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @GetMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> getSettings(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getSettings(roomId));
    }

    @Operation(summary = "회의방 설정 수정", description = "녹화 허용, 화면 공유 허용 등 회의방 설정을 수정합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설정 수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 설정 수정 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 설정 정보를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "settingsNotFound", summary = "설정 정보 없음", value = "{\"code\":\"VIDEO_017\",\"status\":404,\"message\":\"회의실 설정 정보를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PatchMapping("/{roomId}/settings")
    public ApiResponse<RoomSettingsResponse> updateSettings(@PathVariable Long roomId,
                                                            @RequestBody RoomSettingsUpdateRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.updateSettings(roomId, request, userId));
    }

    @Operation(summary = "회의방 태그 추가", description = "회의방에 태그를 추가합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "태그 추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 태그 추가 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 존재하는 태그 이름",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_018\",\"status\":409,\"message\":\"이미 존재하는 태그입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/tags")
    public ApiResponse<Void> addTag(@PathVariable Long roomId,
                                    @Valid @RequestBody TagCreateRequest request,
                                    @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addTag(roomId, request, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 태그 삭제", description = "회의방에서 특정 태그를 삭제합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "태그 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 태그 삭제 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 태그를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "tagNotFound", summary = "존재하지 않는 태그", value = "{\"code\":\"VIDEO_019\",\"status\":404,\"message\":\"존재하지 않는 태그입니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @DeleteMapping("/{roomId}/tags/{tagName}")
    public ApiResponse<Void> removeTag(@PathVariable Long roomId,
                                       @PathVariable String tagName,
                                       @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeTag(roomId, tagName, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "태그로 회의방 검색", description = "특정 태그가 붙은 회의방 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공")
    })
    // CHECK [video-담당자]: searchByTag 반환 타입 List -> Page
    @GetMapping("/tags/{tagName}")
    public ApiResponse<Page<MeetingRoomResponse>> searchByTag(@PathVariable String tagName,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.searchByTag(tagName, pageable));
    }

    @Operation(summary = "회의방 즐겨찾기 추가", description = "회의방을 즐겨찾기 목록에 추가합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "즐겨찾기 추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 즐겨찾기에 추가된 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_020\",\"status\":409,\"message\":\"이미 즐겨찾기에 추가된 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/favorite")
    public ApiResponse<Void> addFavorite(@PathVariable Long roomId,
                                         @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.addFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의방 즐겨찾기 삭제", description = "즐겨찾기 목록에서 회의방을 제거합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "즐겨찾기 삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 즐겨찾기 항목을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "favoriteNotFound", summary = "즐겨찾기 미등록", value = "{\"code\":\"VIDEO_021\",\"status\":404,\"message\":\"즐겨찾기에 등록되지 않은 회의실입니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @DeleteMapping("/{roomId}/favorite")
    public ApiResponse<Void> removeFavorite(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.removeFavorite(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "즐겨찾기 회의방 목록 조회", description = "내가 즐겨찾기한 회의방 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "즐겨찾기 목록 조회 성공")
    })
    // CHECK [video-담당자]: listFavorites 반환 타입 List -> Page
    @GetMapping("/favorites")
    public ApiResponse<Page<MeetingRoomResponse>> listFavorites(@RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.listFavorites(userId, pageable));
    }

    @Operation(summary = "회의 예약 생성", description = "미래 특정 시간에 회의를 예약합니다. 같은 시간대에 중복 예약은 불가합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "예약 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "과거 시간으로 예약하려는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_025\",\"status\":400,\"message\":\"예약 시간은 현재 시간 이후여야 합니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "해당 시간대에 이미 예약된 회의가 있는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_022\",\"status\":409,\"message\":\"해당 시간대에 이미 예약된 회의가 있습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/schedule")
    public ApiResponse<MeetingRoomResponse> schedule(@Valid @RequestBody RoomScheduleRequest request,
                                                     @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.schedule(request, userId));
    }

    @Operation(summary = "예약된 회의 목록 조회", description = "내가 예약한 미래 회의 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "예약 목록 조회 성공")
    })
    // CHECK [video-담당자]: listScheduled 반환 타입 List -> Page
    @GetMapping("/scheduled")
    public ApiResponse<Page<MeetingRoomResponse>> listScheduled(@RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.listScheduled(userId, pageable));
    }

    @Operation(summary = "회의 예약 일정 변경", description = "예약된 회의의 시간을 변경합니다. 예약 상태이며 아직 시작/종료되지 않은 경우만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일정 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "일정 변경 불가 - 예약 회의가 아니거나 이미 시작/종료된 경우 또는 과거 시간 입력",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "notScheduled", summary = "예약 회의실이 아님", value = "{\"code\":\"VIDEO_023\",\"status\":400,\"message\":\"예약 회의실만 일정을 변경할 수 있습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "changeDenied", summary = "시작/종료된 회의실", value = "{\"code\":\"VIDEO_024\",\"status\":400,\"message\":\"시작 또는 종료된 회의실의 일정은 변경할 수 없습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "pastTime", summary = "과거 시간 지정", value = "{\"code\":\"VIDEO_025\",\"status\":400,\"message\":\"예약 시간은 현재 시간 이후여야 합니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 일정 변경 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PatchMapping("/{roomId}/schedule")
    public ApiResponse<MeetingRoomResponse> updateSchedule(@PathVariable Long roomId,
                                                           @RequestParam LocalDateTime scheduledAt,
                                                           @RequestHeader("X-User-Id") Long userId) {
        Instant scheduledInstant = scheduledAt.atZone(ZoneId.of("Asia/Seoul")).toInstant();
        return ApiResponse.ok(meetingRoomService.updateSchedule(roomId, scheduledInstant, userId));
    }

    @Operation(summary = "회의 예약 취소", description = "예약된 회의를 취소합니다. 이미 시작/종료된 회의는 취소할 수 없습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "예약 취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "이미 시작 또는 종료된 회의실은 취소 불가",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_026\",\"status\":400,\"message\":\"시작 또는 종료된 회의실은 취소할 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 취소 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @DeleteMapping("/{roomId}/schedule")
    public ApiResponse<Void> cancelSchedule(@PathVariable Long roomId,
                                            @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.cancelSchedule(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "회의 리마인더 발송", description = "예약된 회의 참가자들에게 알림을 발송합니다. 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리마인더 발송 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "리마인더 발송 불가 - 예약 회의가 아니거나 이미 종료된 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "notScheduled", summary = "예약 회의가 아님", value = "{\"code\":\"VIDEO_027\",\"status\":400,\"message\":\"예약 회의실만 리마인더를 발송할 수 있습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "roomEnded", summary = "이미 종료된 회의실", value = "{\"code\":\"VIDEO_028\",\"status\":400,\"message\":\"종료된 회의실에는 리마인더를 발송할 수 없습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트가 아닌 사용자가 발송 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_015\",\"status\":403,\"message\":\"호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/{roomId}/schedule/remind")
    public ApiResponse<Void> sendReminder(@PathVariable Long roomId,
                                          @RequestHeader("X-User-Id") Long userId) {
        meetingRoomService.sendReminder(roomId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가했던 회의 이력 조회", description = "내가 참가했던 과거 회의 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이력 조회 성공")
    })
    // CHECK [video-담당자]: listHistory 반환 타입 List -> Page
    @GetMapping("/history")
    public ApiResponse<Page<MeetingRoomResponse>> listHistory(@RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(meetingRoomService.listHistory(userId, pageable));
    }

    @Operation(summary = "월별 회의 통계 조회", description = "사용자의 월별 회의 참가 횟수 및 시간 통계를 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "월별 통계 조회 성공")
    })
    @GetMapping("/history/monthly")
    public ApiResponse<List<MonthlyStatsResponse>> getMonthlyStats(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(meetingRoomService.getMonthlyStats(userId));
    }

    @Operation(summary = "회의방 통계 조회", description = "특정 회의방의 참가자 수, 지속 시간 등 통계를 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "통계 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/{roomId}/stats")
    public ApiResponse<RoomStatsResponse> getStats(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getStats(roomId));
    }

    @Operation(summary = "회의방 타임라인 조회", description = "회의방의 참가/퇴장/이벤트 시계열 타임라인을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "타임라인 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/{roomId}/timeline")
    public ApiResponse<List<TimelineEntry>> getTimeline(@PathVariable Long roomId) {
        return ApiResponse.ok(meetingRoomService.getTimeline(roomId));
    }
}
