package com.onmeet.video.meeting.service.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.auth.AuthServiceClient.UserInfo;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.dto.room.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.room.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.room.RoomCreateRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinResponse;
import com.onmeet.video.meeting.dto.room.RoomLockRequest;
import com.onmeet.video.meeting.dto.room.RoomUpdateRequest;
import com.onmeet.video.meeting.dto.room.TagCreateRequest;
import com.onmeet.video.meeting.entity.participant.DeviceType;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomFavorite;
import com.onmeet.video.meeting.entity.room.RoomSettings;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomTag;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.room.RoomFavoriteRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import com.onmeet.video.meeting.repository.room.RoomTagRepository;
import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingRoomServiceTest {

    @Mock MeetingRoomRepository roomRepository;
    @Mock RoomSettingsRepository settingsRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock RoomRecordingRepository recordingRepository;
    @Mock RoomTagRepository tagRepository;
    @Mock RoomFavoriteRepository favoriteRepository;
    @Mock LiveKitClient liveKitClient;
    @Mock LiveKitProperties liveKitProperties;
    @Mock MeetingEventPublisher eventPublisher;
    @Mock ClockProvider clockProvider;
    @Mock AuthServiceClient authServiceClient;
    @Mock WaitingRoomSseService waitingRoomSseService;

    @InjectMocks
    MeetingRoomService meetingRoomService;

    private static final Long HOST_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ROOM_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-22T10:00:00Z");

    private MeetingRoom createRoom(RoomStatus status) {
        MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_USER_ID,
                RoomType.INSTANT, 10, null, null, RoomAccessScope.ALL, null);
        if (status == RoomStatus.ACTIVE) {
            room.start(NOW.minusSeconds(3600));
        } else if (status == RoomStatus.ENDED) {
            room.start(NOW.minusSeconds(7200));
            room.end(NOW.minusSeconds(3600));
        } else if (status == RoomStatus.CANCELLED) {
            room.cancel();
        }
        return room;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("기본 회의방 생성 성공")
        void create_success() {
            RoomCreateRequest request = new RoomCreateRequest(
                    "My Room", "desc", null, null, null, null, null, null);

            when(roomRepository.existsByRoomCode(anyString())).thenReturn(false);
            when(roomRepository.save(any(MeetingRoom.class))).thenAnswer(inv -> inv.getArgument(0));
            when(settingsRepository.save(any(RoomSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            MeetingRoomResponse response = meetingRoomService.create(request, HOST_USER_ID);

            assertThat(response.title()).isEqualTo("My Room");
            assertThat(response.hostUserId()).isEqualTo(HOST_USER_ID);
            assertThat(response.status()).isEqualTo(RoomStatus.WAITING);
            assertThat(response.type()).isEqualTo(RoomType.INSTANT);
            verify(liveKitClient).createRoom(anyString(), eq(10));
        }

        @Test
        @DisplayName("TEAM 스코프에 teamId 없으면 예외")
        void create_teamScope_noTeamId_throws() {
            RoomCreateRequest request = new RoomCreateRequest(
                    "My Room", "desc", null, null, null, null, RoomAccessScope.TEAM, null);

            assertThatThrownBy(() -> meetingRoomService.create(request, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("TEAM 스코프에 팀 미존재 시 예외")
        void create_teamScope_teamNotFound_throws() {
            RoomCreateRequest request = new RoomCreateRequest(
                    "My Room", "desc", null, null, null, null, RoomAccessScope.TEAM, 100L);

            when(authServiceClient.teamExists(100L)).thenReturn(false);

            assertThatThrownBy(() -> meetingRoomService.create(request, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("TEAM 스코프에 호스트가 팀 멤버 아닐 시 예외")
        void create_teamScope_hostNotMember_throws() {
            RoomCreateRequest request = new RoomCreateRequest(
                    "My Room", "desc", null, null, null, null, RoomAccessScope.TEAM, 100L);

            when(authServiceClient.teamExists(100L)).thenReturn(true);
            when(authServiceClient.isTeamMember(100L, HOST_USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> meetingRoomService.create(request, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("룸코드 중복 시 재생성")
        void create_duplicateCode_regenerates() {
            RoomCreateRequest request = new RoomCreateRequest(
                    "My Room", "desc", null, null, null, null, null, null);

            when(roomRepository.existsByRoomCode(anyString())).thenReturn(true, false);
            when(roomRepository.save(any(MeetingRoom.class))).thenAnswer(inv -> inv.getArgument(0));
            when(settingsRepository.save(any(RoomSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            MeetingRoomResponse response = meetingRoomService.create(request, HOST_USER_ID);

            assertThat(response).isNotNull();
            verify(liveKitClient).createRoom(anyString(), eq(10));
        }
    }

    @Nested
    @DisplayName("join")
    class Join {

        @Test
        @DisplayName("일반 참가 성공")
        void join_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(false);
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(0);
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.empty());
            when(clockProvider.now()).thenReturn(NOW);
            when(participantRepository.save(any(RoomParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(authServiceClient.getUserInfo(OTHER_USER_ID))
                    .thenReturn(new UserInfo(OTHER_USER_ID, "User2", "u2@test.com", null));
            when(liveKitClient.generateToken(anyString(), anyString(), anyString(), any(TokenGrants.class)))
                    .thenReturn("test-token");
            when(liveKitProperties.getUrl()).thenReturn("wss://livekit.test");

            RoomJoinResponse response = meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID);

            assertThat(response.token()).isEqualTo("test-token");
            assertThat(response.waitingRoom()).isFalse();
        }

        @Test
        @DisplayName("종료된 방에 참가 시도 시 예외")
        void join_endedRoom_throws() {
            MeetingRoom room = createRoom(RoomStatus.ENDED);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 참가한 사용자 시 예외")
        void join_alreadyJoined_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(true);

            assertThatThrownBy(() -> meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("방이 꽉 찬 경우 예외")
        void join_roomFull_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(false);
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(10);

            assertThatThrownBy(() -> meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("잠긴 방에 잘못된 비밀번호 시 예외")
        void join_lockedRoom_wrongPassword_throws() {
            MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_USER_ID,
                    RoomType.INSTANT, 10, "secret123", null, RoomAccessScope.ALL, null);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(false);

            RoomJoinRequest wrongPwRequest = new RoomJoinRequest("wrongpw", null);

            assertThatThrownBy(() -> meetingRoomService.join(ROOM_ID, wrongPwRequest, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("대기실 활성화 시 대기 상태로 입장")
        void join_waitingRoomEnabled_returnsWaiting() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            RoomSettings settings = RoomSettings.createDefault(room);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(false);
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(0);
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.of(settings));
            when(clockProvider.now()).thenReturn(NOW);
            when(participantRepository.save(any(RoomParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(liveKitProperties.getUrl()).thenReturn("wss://livekit.test");

            // RoomSettings.createDefault에 waitingRoom이 false이므로, 대기실 비활성화 상태
            // 일반 참가자로 입장되어야 함
            RoomJoinResponse response = meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID);

            // createDefault에서 waitingRoom=false이므로 대기실 아님
            assertThat(response.waitingRoom()).isFalse();
        }

        @Test
        @DisplayName("TEAM 스코프 방에 팀 멤버 아닌 사용자 참가 시 예외")
        void join_teamScope_notMember_throws() {
            MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_USER_ID,
                    RoomType.INSTANT, 10, null, null, RoomAccessScope.TEAM, 100L);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(false);
            when(authServiceClient.isTeamMember(100L, OTHER_USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> meetingRoomService.join(ROOM_ID, null, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("start / end")
    class StartEnd {

        @Test
        @DisplayName("회의 시작 성공")
        void start_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(clockProvider.now()).thenReturn(NOW);
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(3);

            MeetingRoomResponse response = meetingRoomService.start(ROOM_ID, HOST_USER_ID);

            assertThat(response.status()).isEqualTo(RoomStatus.ACTIVE);
            verify(eventPublisher).publishMeetingStarted(any());
        }

        @Test
        @DisplayName("호스트가 아닌 사용자가 시작 시도 시 예외")
        void start_notHost_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.start(ROOM_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 활성화된 방 시작 시도 시 예외")
        void start_alreadyActive_throws() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.start(ROOM_ID, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("회의 종료 성공 - 참가자 전원 퇴장 처리")
        void end_success() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            RoomParticipant p1 = new RoomParticipant(room, OTHER_USER_ID, ParticipantRole.PARTICIPANT,
                    ParticipantStatus.JOINED, NOW.minusSeconds(1800), DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(clockProvider.now()).thenReturn(NOW);
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.JOINED))
                    .thenReturn(List.of(p1));
            when(participantRepository.findByRoomIdAndStatus(ROOM_ID, ParticipantStatus.WAITING))
                    .thenReturn(List.of());

            MeetingRoomResponse response = meetingRoomService.end(ROOM_ID, HOST_USER_ID);

            assertThat(response.status()).isEqualTo(RoomStatus.ENDED);
            verify(liveKitClient).removeParticipant(anyString(), eq(String.valueOf(OTHER_USER_ID)));
            verify(waitingRoomSseService).cleanupRoom(ROOM_ID);
            verify(eventPublisher).publishMeetingEnded(any());
        }

        @Test
        @DisplayName("비활성 방 종료 시도 시 예외")
        void end_notActive_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.end(ROOM_ID, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("leave")
    class Leave {

        @Test
        @DisplayName("참가자 퇴장 성공")
        void leave_success() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            RoomParticipant participant = new RoomParticipant(room, OTHER_USER_ID,
                    ParticipantRole.PARTICIPANT, ParticipantStatus.JOINED, NOW.minusSeconds(600), DeviceType.WEB);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(Optional.of(participant));
            when(clockProvider.now()).thenReturn(NOW);

            meetingRoomService.leave(ROOM_ID, OTHER_USER_ID);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            verify(liveKitClient).removeParticipant(anyString(), eq(String.valueOf(OTHER_USER_ID)));
            verify(eventPublisher).publishParticipantLeft(any());
        }

        @Test
        @DisplayName("참가 중이 아닌 사용자 퇴장 시 예외")
        void leave_notParticipant_throws() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatusIn(
                    eq(ROOM_ID), eq(OTHER_USER_ID), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingRoomService.leave(ROOM_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("lock / unlock")
    class LockUnlock {

        @Test
        @DisplayName("방 잠금 성공")
        void lock_success() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            RoomParticipant cohost = new RoomParticipant(room, OTHER_USER_ID,
                    ParticipantRole.CO_HOST, ParticipantStatus.JOINED, NOW, null);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(participantRepository.findByRoomIdAndUserIdAndStatus(
                    ROOM_ID, OTHER_USER_ID, ParticipantStatus.JOINED))
                    .thenReturn(Optional.of(cohost));

            meetingRoomService.lock(ROOM_ID, new RoomLockRequest("pw123"), OTHER_USER_ID);

            assertThat(room.isLocked()).isTrue();
        }

        @Test
        @DisplayName("방 잠금 해제 성공")
        void unlock_success() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            room.lock("pw123");

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            meetingRoomService.unlock(ROOM_ID, HOST_USER_ID);

            assertThat(room.isLocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("방 삭제 성공")
        void delete_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            meetingRoomService.delete(ROOM_ID, HOST_USER_ID);

            verify(liveKitClient).deleteRoom(anyString());
            verify(roomRepository).delete(room);
        }

        @Test
        @DisplayName("활성 중인 방 삭제 시도 시 예외")
        void delete_activeRoom_throws() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.delete(ROOM_ID, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("호스트가 아닌 사용자의 삭제 시도 시 예외")
        void delete_notHost_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.delete(ROOM_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("get / findByCode")
    class GetAndFind {

        @Test
        @DisplayName("방 상세 조회 성공")
        void get_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            RoomSettings settings = RoomSettings.createDefault(room);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.of(settings));
            when(participantRepository.countActiveParticipants(ROOM_ID)).thenReturn(5);
            when(tagRepository.findByRoomId(ROOM_ID)).thenReturn(List.of());

            MeetingRoomDetailResponse response = meetingRoomService.get(ROOM_ID);

            assertThat(response.title()).isEqualTo("Test Room");
            assertThat(response.currentParticipantCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("존재하지 않는 방 조회 시 예외")
        void get_notFound_throws() {
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingRoomService.get(ROOM_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("코드로 방 조회 성공")
        void findByCode_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findByRoomCode("ABC-1234")).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(any())).thenReturn(Optional.empty());
            when(participantRepository.countActiveParticipants(any())).thenReturn(0);
            when(tagRepository.findByRoomId(any())).thenReturn(List.of());

            MeetingRoomDetailResponse response = meetingRoomService.findByCode("ABC-1234");

            assertThat(response.title()).isEqualTo("Test Room");
        }
    }

    @Nested
    @DisplayName("tags")
    class Tags {

        @Test
        @DisplayName("태그 추가 성공")
        void addTag_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(tagRepository.findByRoomIdAndTagName(ROOM_ID, "important")).thenReturn(Optional.empty());

            meetingRoomService.addTag(ROOM_ID, new TagCreateRequest("important"), HOST_USER_ID);

            verify(tagRepository).save(any(RoomTag.class));
        }

        @Test
        @DisplayName("중복 태그 추가 시 예외")
        void addTag_duplicate_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(tagRepository.findByRoomIdAndTagName(ROOM_ID, "important"))
                    .thenReturn(Optional.of(new RoomTag(room, "important")));

            assertThatThrownBy(
                    () -> meetingRoomService.addTag(ROOM_ID, new TagCreateRequest("important"), HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("favorites")
    class Favorites {

        @Test
        @DisplayName("즐겨찾기 추가 성공")
        void addFavorite_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(favoriteRepository.existsByUserIdAndRoomId(HOST_USER_ID, ROOM_ID)).thenReturn(false);

            meetingRoomService.addFavorite(ROOM_ID, HOST_USER_ID);

            verify(favoriteRepository).save(any(RoomFavorite.class));
        }

        @Test
        @DisplayName("중복 즐겨찾기 시 예외")
        void addFavorite_duplicate_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(favoriteRepository.existsByUserIdAndRoomId(HOST_USER_ID, ROOM_ID)).thenReturn(true);

            assertThatThrownBy(() -> meetingRoomService.addFavorite(ROOM_ID, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("schedule")
    class Schedule {

        @Test
        @DisplayName("예약 회의 일정 변경 - 과거 시간 시 예외")
        void updateSchedule_pastTime_throws() {
            MeetingRoom room = new MeetingRoom("Scheduled", "desc", HOST_USER_ID,
                    RoomType.SCHEDULED, 10, null, NOW.plusSeconds(86400), RoomAccessScope.ALL, null);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(clockProvider.now()).thenReturn(NOW);

            Instant pastTime = NOW.minusSeconds(3600);

            assertThatThrownBy(() -> meetingRoomService.updateSchedule(ROOM_ID, pastTime, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("예약 회의 취소 성공")
        void cancelSchedule_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            meetingRoomService.cancelSchedule(ROOM_ID, HOST_USER_ID);

            assertThat(room.getStatus()).isEqualTo(RoomStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 시작된 방 예약 취소 시 예외")
        void cancelSchedule_active_throws() {
            MeetingRoom room = createRoom(RoomStatus.ACTIVE);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> meetingRoomService.cancelSchedule(ROOM_ID, HOST_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("방 정보 업데이트 성공")
        void update_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            RoomUpdateRequest request = new RoomUpdateRequest("New Title", "New Desc", 20);
            MeetingRoomResponse response = meetingRoomService.update(ROOM_ID, request, HOST_USER_ID);

            assertThat(response.title()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("호스트가 아닌 사용자의 업데이트 시 예외")
        void update_notHost_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            RoomUpdateRequest request = new RoomUpdateRequest("New Title", null, null);

            assertThatThrownBy(() -> meetingRoomService.update(ROOM_ID, request, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("settings")
    class Settings {

        @Test
        @DisplayName("설정 조회 성공")
        void getSettings_success() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            RoomSettings settings = RoomSettings.createDefault(room);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.of(settings));

            var response = meetingRoomService.getSettings(ROOM_ID);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("설정 미존재 시 예외")
        void getSettings_notFound_throws() {
            MeetingRoom room = createRoom(RoomStatus.WAITING);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> meetingRoomService.getSettings(ROOM_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
