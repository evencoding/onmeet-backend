package com.onmeet.video.meeting.controller.invitation;

import com.onmeet.common.dto.ErrorResponse;
import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.invitation.BulkInviteRequest;
import com.onmeet.video.meeting.dto.invitation.InvitationResponse;
import com.onmeet.video.meeting.dto.invitation.InviteRequest;
import com.onmeet.video.meeting.service.invitation.RoomInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Room Invitation", description = "회의방 초대 관리 API")
@RestController
public class RoomInvitationController {

    private final RoomInvitationService invitationService;

    public RoomInvitationController(RoomInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Operation(summary = "회의방 초대", description = "특정 사용자를 회의방에 초대합니다. 자기 자신을 초대할 수 없으며, 이미 대기 중인 초대가 있으면 불가합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 초대 요청 - 종료된 회의실이거나 자기 자신 초대",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomEnded", summary = "종료된 회의실", value = "{\"code\":\"VIDEO_033\",\"status\":400,\"message\":\"종료된 회의실에는 초대를 보낼 수 없습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "selfInvite", summary = "자기 자신 초대 시도", value = "{\"code\":\"VIDEO_034\",\"status\":400,\"message\":\"자기 자신을 초대할 수 없습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 초대 대상 사용자가 존재하지 않는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "inviteeNotFound", summary = "존재하지 않는 사용자", value = "{\"code\":\"VIDEO_036\",\"status\":404,\"message\":\"존재하지 않는 사용자입니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "해당 사용자에게 이미 대기 중인 초대장이 있는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_035\",\"status\":409,\"message\":\"해당 사용자에게 이미 대기 중인 초대장이 있습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/api/rooms/{roomId}/invite")
    public ApiResponse<InvitationResponse> invite(@PathVariable Long roomId,
                                                  @Valid @RequestBody InviteRequest request,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.invite(roomId, request.inviteeUserId(), userId));
    }

    @Operation(summary = "회의방 일괄 초대", description = "여러 사용자를 한 번에 회의방에 초대합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일괄 초대 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "종료된 회의실이거나 자기 자신이 포함된 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomEnded", summary = "종료된 회의실", value = "{\"code\":\"VIDEO_033\",\"status\":400,\"message\":\"종료된 회의실에는 초대를 보낼 수 없습니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "selfInvite", summary = "자기 자신 포함", value = "{\"code\":\"VIDEO_034\",\"status\":400,\"message\":\"자기 자신을 초대할 수 없습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 초대 목록 중 존재하지 않는 사용자가 있는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "userNotFound", summary = "존재하지 않는 사용자 포함", value = "{\"code\":\"VIDEO_037\",\"status\":404,\"message\":\"존재하지 않는 사용자가 포함되어 있습니다\",\"timestamp\":1710000000000}")
                })
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "대상 중 이미 대기 중인 초대장이 있는 사용자가 포함된 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_035\",\"status\":409,\"message\":\"해당 사용자에게 이미 대기 중인 초대장이 있습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/api/rooms/{roomId}/invite/bulk")
    public ApiResponse<List<InvitationResponse>> inviteBulk(@PathVariable Long roomId,
                                                            @Valid @RequestBody BulkInviteRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.inviteBulk(roomId, request.inviteeUserIds(), userId));
    }

    @Operation(summary = "초대 목록 조회", description = "회의방의 전체 초대 목록을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 회의실",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"))
        )
    })
    @GetMapping("/api/rooms/{roomId}/invitations")
    public ApiResponse<List<InvitationResponse>> listInvitations(@PathVariable Long roomId) {
        return ApiResponse.ok(invitationService.listInvitations(roomId));
    }

    @Operation(summary = "초대 수락", description = "나에게 온 초대를 수락합니다. 초대받은 당사자만 처리 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 수락 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "이미 처리된 초대장(수락/거절)인 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_040\",\"status\":400,\"message\":\"이미 처리된 초대장입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "초대받은 당사자가 아닌 사용자가 수락 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_039\",\"status\":403,\"message\":\"초대받은 당사자만 처리할 수 있습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 초대장",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_038\",\"status\":404,\"message\":\"초대장을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/api/invitations/{invitationId}/accept")
    public ApiResponse<InvitationResponse> accept(@PathVariable Long invitationId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.accept(invitationId, userId));
    }

    @Operation(summary = "초대 거절", description = "나에게 온 초대를 거절합니다. 초대받은 당사자만 처리 가능합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 거절 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "이미 처리된 초대장인 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_040\",\"status\":400,\"message\":\"이미 처리된 초대장입니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "초대받은 당사자가 아닌 사용자가 거절 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_039\",\"status\":403,\"message\":\"초대받은 당사자만 처리할 수 있습니다\",\"timestamp\":1710000000000}"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "존재하지 않는 초대장",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"VIDEO_038\",\"status\":404,\"message\":\"초대장을 찾을 수 없습니다\",\"timestamp\":1710000000000}"))
        )
    })
    @PostMapping("/api/invitations/{invitationId}/decline")
    public ApiResponse<InvitationResponse> decline(@PathVariable Long invitationId,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.decline(invitationId, userId));
    }

    @Operation(summary = "초대 취소", description = "내가 보낸 초대를 취소합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초대 취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회의실 또는 초대장을 찾을 수 없는 경우",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(name = "roomNotFound", summary = "존재하지 않는 회의실", value = "{\"code\":\"VIDEO_004\",\"status\":404,\"message\":\"존재하지 않는 회의실입니다\",\"timestamp\":1710000000000}"),
                    @ExampleObject(name = "invitationNotFound", summary = "존재하지 않는 초대장", value = "{\"code\":\"VIDEO_038\",\"status\":404,\"message\":\"초대장을 찾을 수 없습니다\",\"timestamp\":1710000000000}")
                })
        )
    })
    @DeleteMapping("/api/rooms/{roomId}/invite/{inviteeUserId}")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long roomId,
                                              @PathVariable Long inviteeUserId,
                                              @RequestHeader("X-User-Id") Long userId) {
        invitationService.cancelInvitation(roomId, inviteeUserId, userId);
        return ApiResponse.ok(null);
    }
}
