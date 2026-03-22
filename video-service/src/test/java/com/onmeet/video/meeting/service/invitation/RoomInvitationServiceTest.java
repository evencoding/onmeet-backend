package com.onmeet.video.meeting.service.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.meeting.dto.invitation.InvitationResponse;
import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
import com.onmeet.video.meeting.entity.invitation.RoomInvitation;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.event.NotificationEventPublisher;
import com.onmeet.video.meeting.repository.invitation.RoomInvitationRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomInvitationServiceTest {

    @Mock RoomInvitationRepository invitationRepository;
    @Mock MeetingRoomRepository roomRepository;
    @Mock AuthServiceClient authServiceClient;
    @Mock NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    RoomInvitationService invitationService;

    private static final Long HOST_ID = 1L;
    private static final Long INVITEE_ID = 2L;
    private static final Long ROOM_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-22T10:00:00Z");

    private MeetingRoom createRoom() {
        return new MeetingRoom("Test Room", "desc", HOST_ID,
                RoomType.INSTANT, 10, null, null, RoomAccessScope.ALL, null);
    }

    @Nested
    @DisplayName("invite")
    class Invite {

        @Test
        @DisplayName("초대 성공")
        void invite_success() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    ROOM_ID, INVITEE_ID, InvitationStatus.PENDING)).thenReturn(false);
            when(authServiceClient.userExists(INVITEE_ID)).thenReturn(true);
            when(invitationRepository.save(any(RoomInvitation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InvitationResponse response = invitationService.invite(ROOM_ID, INVITEE_ID, HOST_ID);

            assertThat(response.inviteeUserId()).isEqualTo(INVITEE_ID);
            assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
            verify(notificationEventPublisher).publishNotification(any());
        }

        @Test
        @DisplayName("종료된 방에 초대 시 예외")
        void invite_endedRoom_throws() {
            MeetingRoom room = createRoom();
            room.start(NOW.minusSeconds(7200));
            room.end(NOW.minusSeconds(3600));

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> invitationService.invite(ROOM_ID, INVITEE_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("자기 자신 초대 시 예외")
        void invite_self_throws() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> invitationService.invite(ROOM_ID, HOST_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 PENDING 상태 초대 존재 시 예외")
        void invite_alreadyPending_throws() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    ROOM_ID, INVITEE_ID, InvitationStatus.PENDING)).thenReturn(true);

            assertThatThrownBy(() -> invitationService.invite(ROOM_ID, INVITEE_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 초대 시 예외")
        void invite_userNotFound_throws() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    ROOM_ID, INVITEE_ID, InvitationStatus.PENDING)).thenReturn(false);
            when(authServiceClient.userExists(INVITEE_ID)).thenReturn(false);

            assertThatThrownBy(() -> invitationService.invite(ROOM_ID, INVITEE_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("inviteBulk")
    class InviteBulk {

        @Test
        @DisplayName("벌크 초대 성공 - 자기 자신과 중복은 건너뜀")
        void inviteBulk_success_skipsSelfAndDuplicate() {
            MeetingRoom room = createRoom();
            Long user3 = 3L;

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(authServiceClient.batchUserExists(List.of(HOST_ID, INVITEE_ID, user3)))
                    .thenReturn(Map.of(HOST_ID, true, INVITEE_ID, true, user3, true));
            // HOST_ID는 자기 자신이므로 건너뜀
            when(invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    ROOM_ID, INVITEE_ID, InvitationStatus.PENDING)).thenReturn(true); // 이미 PENDING
            when(invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    ROOM_ID, user3, InvitationStatus.PENDING)).thenReturn(false);
            when(invitationRepository.save(any(RoomInvitation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<InvitationResponse> results = invitationService.inviteBulk(
                    ROOM_ID, List.of(HOST_ID, INVITEE_ID, user3), HOST_ID);

            // HOST_ID=self skip, INVITEE_ID=pending skip, user3=success
            assertThat(results).hasSize(1);
            assertThat(results.get(0).inviteeUserId()).isEqualTo(user3);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 포함 시 예외")
        void inviteBulk_nonExistentUser_throws() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(authServiceClient.batchUserExists(List.of(INVITEE_ID, 99L)))
                    .thenReturn(Map.of(INVITEE_ID, true, 99L, false));

            assertThatThrownBy(
                    () -> invitationService.inviteBulk(ROOM_ID, List.of(INVITEE_ID, 99L), HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("accept / decline")
    class AcceptDecline {

        @Test
        @DisplayName("초대 수락 성공")
        void accept_success() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            InvitationResponse response = invitationService.accept(1L, INVITEE_ID);

            assertThat(response.status()).isEqualTo(InvitationStatus.ACCEPTED);
            verify(notificationEventPublisher).publishNotification(any());
        }

        @Test
        @DisplayName("초대받은 사용자가 아닌 경우 수락 시 예외")
        void accept_notInvitee_throws() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.accept(1L, 99L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 처리된 초대 수락 시 예외")
        void accept_alreadyProcessed_throws() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);
            invitation.accept(); // 이미 수락됨

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            assertThatThrownBy(() -> invitationService.accept(1L, INVITEE_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("초대 거절 성공")
        void decline_success() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);

            when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

            InvitationResponse response = invitationService.decline(1L, INVITEE_ID);

            assertThat(response.status()).isEqualTo(InvitationStatus.DECLINED);
            verify(notificationEventPublisher).publishNotification(any());
        }
    }

    @Nested
    @DisplayName("cancelInvitation")
    class Cancel {

        @Test
        @DisplayName("초대 취소 성공")
        void cancelInvitation_success() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.findByRoomIdAndInviteeUserId(ROOM_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invitation));

            invitationService.cancelInvitation(ROOM_ID, INVITEE_ID, HOST_ID);

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
            verify(notificationEventPublisher).publishNotification(any());
        }

        @Test
        @DisplayName("호스트가 아닌 사용자의 초대 취소 시 예외")
        void cancelInvitation_notHost_throws() {
            MeetingRoom room = createRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(
                    () -> invitationService.cancelInvitation(ROOM_ID, INVITEE_ID, 99L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 처리된 초대 취소 시 예외")
        void cancelInvitation_alreadyProcessed_throws() {
            MeetingRoom room = createRoom();
            RoomInvitation invitation = new RoomInvitation(room, HOST_ID, INVITEE_ID);
            invitation.accept();

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.findByRoomIdAndInviteeUserId(ROOM_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invitation));

            assertThatThrownBy(
                    () -> invitationService.cancelInvitation(ROOM_ID, INVITEE_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("listInvitations")
    class ListInvitations {

        @Test
        @DisplayName("초대 목록 조회 성공")
        void listInvitations_success() {
            MeetingRoom room = createRoom();
            RoomInvitation inv1 = new RoomInvitation(room, HOST_ID, INVITEE_ID);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(invitationRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(inv1));

            List<InvitationResponse> result = invitationService.listInvitations(ROOM_ID);

            assertThat(result).hasSize(1);
        }
    }
}
