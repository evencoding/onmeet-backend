package com.onmeet.video.meeting.service.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.auth.AuthServiceClient.UserInfo;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.dto.participant.ParticipantRoleUpdateRequest;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.entity.participant.DeviceType;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.NotificationEventPublisher;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomParticipantServiceTest {

    @Mock RoomParticipantRepository participantRepository;
    @Mock MeetingRoomRepository roomRepository;
    @Mock LiveKitClient liveKitClient;
    @Mock LiveKitProperties liveKitProperties;
    @Mock MeetingEventPublisher eventPublisher;
    @Mock ClockProvider clockProvider;
    @Mock AuthServiceClient authServiceClient;
    @Mock WaitingRoomSseService waitingRoomSseService;
    @Mock NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    RoomParticipantService participantService;

    private static final Long HOST_ID = 1L;
    private static final Long USER_ID = 2L;
    private static final Long TARGET_ID = 3L;
    private static final Long ROOM_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-22T10:00:00Z");

    private MeetingRoom createActiveRoom() {
        MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_ID,
                RoomType.INSTANT, 10, null, null, RoomAccessScope.ALL, null);
        room.start(NOW.minusSeconds(3600));
        return room;
    }

    @Nested
    @DisplayName("listCurrent")
    class ListCurrent {

        @Test
        @DisplayName("현재 참가자 목록 조회 성공")
        void listCurrent_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant p = new RoomParticipant(room, USER_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.JOINED))
                    .thenReturn(List.of(p));

            List<RoomParticipantResponse> result = participantService.listCurrent(ROOM_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("역할 변경 성공")
        void updateRole_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant target = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.JOINED))
                    .thenReturn(Optional.of(target));

            var request = new ParticipantRoleUpdateRequest(ParticipantRole.CO_HOST);
            RoomParticipantResponse response = participantService.updateRole(ROOM_ID, TARGET_ID, request, HOST_ID);

            assertThat(response.role()).isEqualTo(ParticipantRole.CO_HOST);
        }

        @Test
        @DisplayName("HOST 역할 변경 시도 시 예외")
        void updateRole_hostRole_throws() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant host = new RoomParticipant(room, HOST_ID, ParticipantRole.HOST,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, HOST_ID, ParticipantStatus.JOINED))
                    .thenReturn(Optional.of(host));

            var request = new ParticipantRoleUpdateRequest(ParticipantRole.PARTICIPANT);

            assertThatThrownBy(() -> participantService.updateRole(ROOM_ID, HOST_ID, request, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("참가자가 아닌 사용자 역할 변경 시 예외")
        void updateRole_notFound_throws() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.JOINED))
                    .thenReturn(Optional.empty());

            var request = new ParticipantRoleUpdateRequest(ParticipantRole.CO_HOST);

            assertThatThrownBy(() -> participantService.updateRole(ROOM_ID, TARGET_ID, request, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("kick")
    class Kick {

        @Test
        @DisplayName("참가자 강퇴 성공")
        void kick_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant target = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW.minusSeconds(600), DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.JOINED))
                    .thenReturn(Optional.of(target));
            when(clockProvider.now()).thenReturn(NOW);

            participantService.kick(ROOM_ID, TARGET_ID, HOST_ID);

            assertThat(target.getStatus()).isEqualTo(ParticipantStatus.KICKED);
            verify(liveKitClient).removeParticipant(anyString(), eq(String.valueOf(TARGET_ID)));
            verify(eventPublisher).publishParticipantLeft(any());
        }

        @Test
        @DisplayName("HOST 강퇴 시도 시 예외")
        void kick_host_throws() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> participantService.kick(ROOM_ID, HOST_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("mute / unmute")
    class MuteUnmute {

        @Test
        @DisplayName("개별 음소거 성공")
        void mute_success() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(TARGET_ID), any())).thenReturn(true);

            participantService.mute(ROOM_ID, TARGET_ID, HOST_ID);

            verify(liveKitClient).muteParticipantTrack(
                    room.getLivekitRoomName(), String.valueOf(TARGET_ID), "audio", true);
        }

        @Test
        @DisplayName("참가하지 않은 사용자 음소거 시 예외")
        void mute_notParticipant_throws() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(TARGET_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> participantService.mute(ROOM_ID, TARGET_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("전체 음소거 - HOST 제외")
        void muteAll_excludesHost() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant hostP = new RoomParticipant(room, HOST_ID, ParticipantRole.HOST,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);
            RoomParticipant user1 = new RoomParticipant(room, USER_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);
            RoomParticipant user2 = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.JOINED))
                    .thenReturn(List.of(hostP, user1, user2));

            participantService.muteAll(ROOM_ID, HOST_ID);

            verify(liveKitClient, times(2)).muteParticipantTrack(anyString(), anyString(), eq("audio"), eq(true));
            verify(liveKitClient, never()).muteParticipantTrack(
                    anyString(), eq(String.valueOf(HOST_ID)), eq("audio"), eq(true));
        }
    }

    @Nested
    @DisplayName("waiting room")
    class WaitingRoom {

        @Test
        @DisplayName("대기 참가자 수락 성공")
        void admitWaiting_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant waiting = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW.minusSeconds(60), DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.WAITING))
                    .thenReturn(Optional.of(waiting));
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(3);
            when(clockProvider.now()).thenReturn(NOW);
            when(authServiceClient.getUserInfo(TARGET_ID))
                    .thenReturn(new UserInfo(TARGET_ID, "Target User", "t@test.com", null));
            when(liveKitClient.generateToken(anyString(), anyString(), anyString(), any(TokenGrants.class)))
                    .thenReturn("admit-token");
            when(liveKitProperties.getUrl()).thenReturn("wss://livekit.test");

            participantService.admitWaiting(ROOM_ID, TARGET_ID, HOST_ID);

            assertThat(waiting.getStatus()).isEqualTo(ParticipantStatus.JOINED);
            verify(waitingRoomSseService).sendAdmittedEvent(eq(ROOM_ID), eq(TARGET_ID), eq("admit-token"),
                    eq("wss://livekit.test"), anyString());
            verify(eventPublisher).publishParticipantJoined(any());
            verify(notificationEventPublisher).publishNotification(any());
        }

        @Test
        @DisplayName("방이 꽉 찬 경우 대기 참가자 수락 시 예외")
        void admitWaiting_roomFull_throws() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant waiting = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.WAITING))
                    .thenReturn(Optional.of(waiting));
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(10);

            assertThatThrownBy(() -> participantService.admitWaiting(ROOM_ID, TARGET_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("대기 참가자 거절 성공")
        void rejectWaiting_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant waiting = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.WAITING))
                    .thenReturn(Optional.of(waiting));
            when(clockProvider.now()).thenReturn(NOW);

            participantService.rejectWaiting(ROOM_ID, TARGET_ID, HOST_ID);

            assertThat(waiting.getStatus()).isEqualTo(ParticipantStatus.KICKED);
            verify(waitingRoomSseService).sendRejectedEvent(ROOM_ID, TARGET_ID);
            verify(notificationEventPublisher).publishNotification(any());
        }

        @Test
        @DisplayName("대기 참가자가 아닌 사용자 수락 시 예외")
        void admitWaiting_notWaiting_throws() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(ROOM_ID, TARGET_ID, ParticipantStatus.WAITING))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> participantService.admitWaiting(ROOM_ID, TARGET_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("대기 참가자 전체 수락 - 정원 제한 적용")
        void admitAllWaiting_respectsCapacity() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant w1 = new RoomParticipant(room, 10L, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);
            RoomParticipant w2 = new RoomParticipant(room, 11L, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);
            RoomParticipant w3 = new RoomParticipant(room, 12L, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.WAITING))
                    .thenReturn(List.of(w1, w2, w3));
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(9);
            when(clockProvider.now()).thenReturn(NOW);
            when(authServiceClient.getBatchUserInfo(any())).thenReturn(List.of(
                    new UserInfo(10L, "U10", "u10@test.com", null)));
            when(liveKitClient.generateToken(anyString(), anyString(), anyString(), any(TokenGrants.class)))
                    .thenReturn("token");
            when(liveKitProperties.getUrl()).thenReturn("wss://livekit.test");

            participantService.admitAllWaiting(ROOM_ID, HOST_ID);

            // maxParticipants=10, current=9, available=1 -> 1명만 수락
            assertThat(w1.getStatus()).isEqualTo(ParticipantStatus.JOINED);
            assertThat(w2.getStatus()).isEqualTo(ParticipantStatus.WAITING);
        }
    }

    @Nested
    @DisplayName("listWaiting")
    class ListWaiting {

        @Test
        @DisplayName("대기 참가자 목록 조회 성공")
        void listWaiting_success() {
            MeetingRoom room = createActiveRoom();
            RoomParticipant waiting = new RoomParticipant(room, TARGET_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.WAITING, NOW, DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.WAITING))
                    .thenReturn(List.of(waiting));

            List<RoomParticipantResponse> result = participantService.listWaiting(ROOM_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(ParticipantStatus.WAITING);
        }
    }
}
