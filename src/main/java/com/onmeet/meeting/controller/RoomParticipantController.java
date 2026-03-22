package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.ParticipantRoleUpdateRequest;
import com.onmeet.meeting.dto.RoomParticipantResponse;
import com.onmeet.meeting.service.RoomParticipantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomParticipantController {

    private final RoomParticipantService participantService;

    public RoomParticipantController(RoomParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping("/participants")
    public ApiResponse<List<RoomParticipantResponse>> listCurrent(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listCurrent(roomId));
    }

    @GetMapping("/participants/history")
    public ApiResponse<List<RoomParticipantResponse>> listHistory(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listHistory(roomId));
    }

    @PatchMapping("/participants/{userId}/role")
    public ApiResponse<RoomParticipantResponse> updateRole(@PathVariable Long roomId,
                                                           @PathVariable Long userId,
                                                           @Valid @RequestBody ParticipantRoleUpdateRequest request,
                                                           @RequestHeader("X-User-Id") Long requesterId) {
        return ApiResponse.ok(participantService.updateRole(roomId, userId, request, requesterId));
    }

    @PostMapping("/participants/{userId}/kick")
    public ApiResponse<Void> kick(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.kick(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/participants/{userId}/mute")
    public ApiResponse<Void> mute(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.mute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/participants/{userId}/unmute")
    public ApiResponse<Void> unmute(@PathVariable Long roomId,
                                    @PathVariable Long userId,
                                    @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/mute-all")
    public ApiResponse<Void> muteAll(@PathVariable Long roomId,
                                     @RequestHeader("X-User-Id") Long requesterId) {
        participantService.muteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/unmute-all")
    public ApiResponse<Void> unmuteAll(@PathVariable Long roomId,
                                       @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmuteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/disable-video-all")
    public ApiResponse<Void> disableVideoAll(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.disableVideoAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/waiting")
    public ApiResponse<List<RoomParticipantResponse>> listWaiting(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listWaiting(roomId));
    }

    @PostMapping("/waiting/{userId}/admit")
    public ApiResponse<Void> admitWaiting(@PathVariable Long roomId,
                                          @PathVariable Long userId,
                                          @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/waiting/{userId}/reject")
    public ApiResponse<Void> rejectWaiting(@PathVariable Long roomId,
                                           @PathVariable Long userId,
                                           @RequestHeader("X-User-Id") Long requesterId) {
        participantService.rejectWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/waiting/admit-all")
    public ApiResponse<Void> admitAllWaiting(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitAllWaiting(roomId, requesterId);
        return ApiResponse.ok(null);
    }
}
