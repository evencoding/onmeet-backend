package com.onmeet.video.meeting.controller.participant;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.participant.ParticipantRoleUpdateRequest;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.service.participant.RoomParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Room Participant", description = "회의방 참가자 관리 API")
@RestController
// CHECK [video-담당자]: URL 패턴 /api/rooms → /v1/rooms 변경 (gateway /video/v1/** 라우팅 통일)
@RequestMapping("/v1/rooms/{roomId}")
public class RoomParticipantController {

    private final RoomParticipantService participantService;

    public RoomParticipantController(RoomParticipantService participantService) {
        this.participantService = participantService;
    }

    @Operation(summary = "현재 참가자 목록 조회", description = "현재 회의방에 참가 중인 참가자 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참가자 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/participants")
    public ApiResponse<List<RoomParticipantResponse>> listCurrent(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listCurrent(roomId));
    }

    @Operation(summary = "참가자 이력 조회", description = "회의방에 입장했던 모든 참가자 이력을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "참가자 이력 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    // CHECK [video-담당자]: listHistory 반환 타입 List -> Page
    @GetMapping("/participants/history")
    public ApiResponse<Page<RoomParticipantResponse>> listHistory(@PathVariable Long roomId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(participantService.listHistory(roomId, pageable));
    }

    @Operation(summary = "참가자 역할 변경", description = "참가자의 역할(공동 호스트 등)을 변경합니다. 호스트의 역할은 변경할 수 없습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "역할 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트의 역할을 변경하려는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_030\",\"status\":403,\"message\":\"호스트의 역할은 변경할 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "participantNotFound", summary = "존재하지 않는 참가자", value = "{\"code\":\"VIDEO_029\",\"status\":404,\"message\":\"해당 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PatchMapping("/participants/{userId}/role")
    public ApiResponse<RoomParticipantResponse> updateRole(@PathVariable Long roomId,
                                                           @PathVariable Long userId,
                                                           @Valid @RequestBody ParticipantRoleUpdateRequest request,
                                                           @RequestHeader("X-User-Id") Long requesterId) {
        return ApiResponse.ok(participantService.updateRole(roomId, userId, request, requesterId));
    }

    @Operation(summary = "참가자 강퇴", description = "회의방에서 특정 참가자를 강제 퇴장시킵니다. 호스트는 강퇴할 수 없습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "강퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트를 강퇴하려는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_031\",\"status\":403,\"message\":\"호스트는 강제 퇴장시킬 수 없습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "participantNotFound", summary = "존재하지 않는 참가자", value = "{\"code\":\"VIDEO_029\",\"status\":404,\"message\":\"해당 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/participants/{userId}/kick")
    public ApiResponse<Void> kick(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.kick(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가자 음소거", description = "특정 참가자의 마이크를 음소거합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "음소거 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "participantNotFound", summary = "존재하지 않는 참가자", value = "{\"code\":\"VIDEO_029\",\"status\":404,\"message\":\"해당 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/participants/{userId}/mute")
    public ApiResponse<Void> mute(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.mute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가자 음소거 해제", description = "특정 참가자의 음소거를 해제합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "음소거 해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "participantNotFound", summary = "존재하지 않는 참가자", value = "{\"code\":\"VIDEO_029\",\"status\":404,\"message\":\"해당 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/participants/{userId}/unmute")
    public ApiResponse<Void> unmute(@PathVariable Long roomId,
                                    @PathVariable Long userId,
                                    @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 음소거", description = "회의방의 모든 참가자를 음소거합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 음소거 성공"),
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
    @PostMapping("/mute-all")
    public ApiResponse<Void> muteAll(@PathVariable Long roomId,
                                     @RequestHeader("X-User-Id") Long requesterId) {
        participantService.muteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 음소거 해제", description = "회의방의 모든 참가자의 음소거를 해제합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 음소거 해제 성공"),
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
    @PostMapping("/unmute-all")
    public ApiResponse<Void> unmuteAll(@PathVariable Long roomId,
                                       @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmuteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 비디오 끄기", description = "회의방의 모든 참가자의 비디오를 비활성화합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 비디오 비활성화 성공"),
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
    @PostMapping("/disable-video-all")
    public ApiResponse<Void> disableVideoAll(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.disableVideoAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 참가자 목록 조회", description = "대기실에 입장 승인을 기다리는 참가자 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "대기 참가자 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/waiting")
    public ApiResponse<List<RoomParticipantResponse>> listWaiting(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listWaiting(roomId));
    }

    @Operation(summary = "대기실 참가자 승인", description = "대기 중인 참가자의 입장을 승인합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "입장 승인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 대기 중인 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "waitingNotFound", summary = "대기 중인 참가자 없음", value = "{\"code\":\"VIDEO_032\",\"status\":404,\"message\":\"대기 중인 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/waiting/{userId}/admit")
    public ApiResponse<Void> admitWaiting(@PathVariable Long roomId,
                                          @PathVariable Long userId,
                                          @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 참가자 거절", description = "대기 중인 참가자의 입장을 거절합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "입장 거절 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "호스트 또는 공동 호스트가 아닌 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_016\",\"status\":403,\"message\":\"호스트 또는 공동 호스트만 수행할 수 있는 작업입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 대기 중인 참가자를 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "waitingNotFound", summary = "대기 중인 참가자 없음", value = "{\"code\":\"VIDEO_032\",\"status\":404,\"message\":\"대기 중인 참가자를 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @PostMapping("/waiting/{userId}/reject")
    public ApiResponse<Void> rejectWaiting(@PathVariable Long roomId,
                                           @PathVariable Long userId,
                                           @RequestHeader("X-User-Id") Long requesterId) {
        participantService.rejectWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 전체 승인", description = "대기 중인 모든 참가자의 입장을 일괄 승인합니다. 호스트 또는 공동 호스트만 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 승인 성공"),
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
    @PostMapping("/waiting/admit-all")
    public ApiResponse<Void> admitAllWaiting(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitAllWaiting(roomId, requesterId);
        return ApiResponse.ok(null);
    }
}
