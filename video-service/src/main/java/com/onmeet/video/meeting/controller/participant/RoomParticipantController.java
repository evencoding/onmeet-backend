package com.onmeet.video.meeting.controller.participant;

import com.onmeet.video.common.response.ApiResponse;
import com.onmeet.video.meeting.dto.participant.ParticipantRoleUpdateRequest;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.service.participant.RoomParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Room Participant", description = "회의방 참가자 관리 API")
@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomParticipantController {

    private final RoomParticipantService participantService;

    public RoomParticipantController(RoomParticipantService participantService) {
        this.participantService = participantService;
    }

    @Operation(summary = "현재 참가자 목록 조회")
    @GetMapping("/participants")
    public ApiResponse<List<RoomParticipantResponse>> listCurrent(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listCurrent(roomId));
    }

    @Operation(summary = "참가자 이력 조회")
    @GetMapping("/participants/history")
    public ApiResponse<List<RoomParticipantResponse>> listHistory(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listHistory(roomId));
    }

    @Operation(summary = "참가자 역할 변경")
    @PatchMapping("/participants/{userId}/role")
    public ApiResponse<RoomParticipantResponse> updateRole(@PathVariable Long roomId,
                                                           @PathVariable Long userId,
                                                           @Valid @RequestBody ParticipantRoleUpdateRequest request,
                                                           @RequestHeader("X-User-Id") Long requesterId) {
        return ApiResponse.ok(participantService.updateRole(roomId, userId, request, requesterId));
    }

    @Operation(summary = "참가자 강퇴")
    @PostMapping("/participants/{userId}/kick")
    public ApiResponse<Void> kick(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.kick(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가자 음소거")
    @PostMapping("/participants/{userId}/mute")
    public ApiResponse<Void> mute(@PathVariable Long roomId,
                                  @PathVariable Long userId,
                                  @RequestHeader("X-User-Id") Long requesterId) {
        participantService.mute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "참가자 음소거 해제")
    @PostMapping("/participants/{userId}/unmute")
    public ApiResponse<Void> unmute(@PathVariable Long roomId,
                                    @PathVariable Long userId,
                                    @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmute(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 음소거")
    @PostMapping("/mute-all")
    public ApiResponse<Void> muteAll(@PathVariable Long roomId,
                                     @RequestHeader("X-User-Id") Long requesterId) {
        participantService.muteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 음소거 해제")
    @PostMapping("/unmute-all")
    public ApiResponse<Void> unmuteAll(@PathVariable Long roomId,
                                       @RequestHeader("X-User-Id") Long requesterId) {
        participantService.unmuteAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "전체 비디오 끄기")
    @PostMapping("/disable-video-all")
    public ApiResponse<Void> disableVideoAll(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.disableVideoAll(roomId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 참가자 목록 조회")
    @GetMapping("/waiting")
    public ApiResponse<List<RoomParticipantResponse>> listWaiting(@PathVariable Long roomId) {
        return ApiResponse.ok(participantService.listWaiting(roomId));
    }

    @Operation(summary = "대기실 참가자 승인")
    @PostMapping("/waiting/{userId}/admit")
    public ApiResponse<Void> admitWaiting(@PathVariable Long roomId,
                                          @PathVariable Long userId,
                                          @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 참가자 거절")
    @PostMapping("/waiting/{userId}/reject")
    public ApiResponse<Void> rejectWaiting(@PathVariable Long roomId,
                                           @PathVariable Long userId,
                                           @RequestHeader("X-User-Id") Long requesterId) {
        participantService.rejectWaiting(roomId, userId, requesterId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "대기실 전체 승인")
    @PostMapping("/waiting/admit-all")
    public ApiResponse<Void> admitAllWaiting(@PathVariable Long roomId,
                                             @RequestHeader("X-User-Id") Long requesterId) {
        participantService.admitAllWaiting(roomId, requesterId);
        return ApiResponse.ok(null);
    }
}
