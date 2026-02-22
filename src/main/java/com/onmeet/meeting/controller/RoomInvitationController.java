package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.BulkInviteRequest;
import com.onmeet.meeting.dto.InvitationResponse;
import com.onmeet.meeting.dto.InviteRequest;
import com.onmeet.meeting.service.RoomInvitationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomInvitationController {

    private final RoomInvitationService invitationService;

    public RoomInvitationController(RoomInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/rooms/{roomId}/invite")
    public ApiResponse<InvitationResponse> invite(@PathVariable Long roomId,
                                                  @Valid @RequestBody InviteRequest request,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.invite(roomId, request.inviteeUserId(), userId));
    }

    @PostMapping("/api/rooms/{roomId}/invite/bulk")
    public ApiResponse<List<InvitationResponse>> inviteBulk(@PathVariable Long roomId,
                                                            @Valid @RequestBody BulkInviteRequest request,
                                                            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.inviteBulk(roomId, request.inviteeUserIds(), userId));
    }

    @GetMapping("/api/rooms/{roomId}/invitations")
    public ApiResponse<List<InvitationResponse>> listInvitations(@PathVariable Long roomId) {
        return ApiResponse.ok(invitationService.listInvitations(roomId));
    }

    @PostMapping("/api/invitations/{invitationId}/accept")
    public ApiResponse<InvitationResponse> accept(@PathVariable Long invitationId,
                                                  @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.accept(invitationId, userId));
    }

    @PostMapping("/api/invitations/{invitationId}/decline")
    public ApiResponse<InvitationResponse> decline(@PathVariable Long invitationId,
                                                   @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(invitationService.decline(invitationId, userId));
    }

    @DeleteMapping("/api/rooms/{roomId}/invite/{inviteeUserId}")
    public ApiResponse<Void> cancelInvitation(@PathVariable Long roomId,
                                              @PathVariable Long inviteeUserId,
                                              @RequestHeader("X-User-Id") Long userId) {
        invitationService.cancelInvitation(roomId, inviteeUserId, userId);
        return ApiResponse.ok(null);
    }
}
