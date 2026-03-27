package com.onmeet.video.meeting.repository.invitation;

import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
import com.onmeet.video.meeting.entity.invitation.RoomInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {

    List<RoomInvitation> findByRoomId(Long roomId);

    List<RoomInvitation> findByRoomIdAndStatus(Long roomId, InvitationStatus status);

    Optional<RoomInvitation> findByRoomIdAndInviteeUserId(Long roomId, Long inviteeUserId);

    boolean existsByRoomIdAndInviteeUserIdAndStatus(Long roomId, Long inviteeUserId, InvitationStatus status);

    List<RoomInvitation> findByInviteeUserIdAndStatusIn(Long inviteeUserId, List<InvitationStatus> statuses);
}
