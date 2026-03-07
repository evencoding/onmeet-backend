package com.onmeet.video.meeting.controller.invitation;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.invitation.BulkInviteRequest;
import com.onmeet.video.meeting.dto.invitation.InvitationResponse;
import com.onmeet.video.meeting.dto.invitation.InviteRequest;
import com.onmeet.video.meeting.service.invitation.RoomInvitationService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "회의방 초대")
    @PostMapping("/api/rooms/{roomId}/invite")
    public ApiResponse<InvitationResponse> invite(@PathVariable Long roomId,
                                                  @Valid @RequestBody InviteRequest request,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.invite(roomId, request.inviteeUserId(), userId));
    }

    @Operation(summary = "회의방 일괄 초대")
    @PostMapping("/api/rooms/{roomId}/invite/bulk")
    public ApiResponse<List<InvitationResponse>> inviteBulk(@PathVariable Long roomId,
                                                            @Valid @RequestBody BulkInviteRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.inviteBulk(roomId, request.inviteeUserIds(), userId));
    }

    @Operation(summary = "초대 목록 조회")
    @GetMapping("/api/rooms/{roomId}/invitations")
    public ApiResponse<List<InvitationResponse>> listInvitations(@PathVariable Long roomId) {
        return ApiResponse.ok(invitationService.listInvitations(roomId));
    }

    @Operation(summary = "초대 수락")
    @PostMapping("/api/invitations/{invitationId}/accept")
    public ApiResponse<InvitationResponse> accept(@PathVariable Long invitationId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.accept(invitationId, userId));
    }

    @Operation(summary = "초대 거절")
    @PostMapping("/api/invitations/{invitationId}/decline")
    public ApiResponse<InvitationResponse> decline(@PathVariable Long invitationId,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.decline(invitationId, userId));
    }

    @Operation(summary = "초대 취소")
    @DeleteMapping("/api/rooms/{roomId}/invite/{inviteeUserId}")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long roomId,
                                              @PathVariable Long inviteeUserId,
                                              @RequestHeader("X-User-Id") Long userId) {
        invitationService.cancelInvitation(roomId, inviteeUserId, userId);
        return ApiResponse.ok(null);
    }
}
