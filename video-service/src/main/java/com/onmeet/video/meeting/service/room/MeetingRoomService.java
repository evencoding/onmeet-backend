package com.onmeet.video.meeting.service.room;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import com.onmeet.video.meeting.dto.room.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.room.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.room.MonthlyStatsResponse;
import com.onmeet.video.meeting.dto.room.RoomCreateRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinRequest;
import com.onmeet.video.meeting.dto.room.RoomJoinResponse;
import com.onmeet.video.meeting.dto.room.RoomLockRequest;
import com.onmeet.video.meeting.dto.room.RoomScheduleRequest;
import com.onmeet.video.meeting.dto.room.RoomSettingsResponse;
import com.onmeet.video.meeting.dto.room.RoomSettingsUpdateRequest;
import com.onmeet.video.meeting.dto.room.RoomStatsResponse;
import com.onmeet.video.meeting.dto.room.RoomUpdateRequest;
import com.onmeet.video.meeting.dto.room.TagCreateRequest;
import com.onmeet.video.meeting.dto.room.TimelineEntry;
import com.onmeet.video.meeting.entity.participant.DeviceType;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.room.RoomFavorite;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.entity.room.RoomSettings;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomTag;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.room.RoomFavoriteRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.invitation.RoomInvitationRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import com.onmeet.video.meeting.repository.room.RoomTagRepository;
import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
import com.onmeet.video.meeting.entity.invitation.RoomInvitation;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingRoomService {

    private static final Logger log = LoggerFactory.getLogger(MeetingRoomService.class);

    private final MeetingRoomRepository roomRepository;
    private final RoomSettingsRepository settingsRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomRecordingRepository recordingRepository;
    private final RoomTagRepository tagRepository;
    private final RoomFavoriteRepository favoriteRepository;
    private final RoomInvitationRepository invitationRepository;
    private final LiveKitClient liveKitClient;
    private final LiveKitProperties liveKitProperties;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final AuthServiceClient authServiceClient;
    private final WaitingRoomSseService waitingRoomSseService;

    public MeetingRoomService(MeetingRoomRepository roomRepository,
            RoomSettingsRepository settingsRepository,
            RoomParticipantRepository participantRepository,
            RoomRecordingRepository recordingRepository,
            RoomTagRepository tagRepository,
            RoomFavoriteRepository favoriteRepository,
            RoomInvitationRepository invitationRepository,
            LiveKitClient liveKitClient,
            LiveKitProperties liveKitProperties,
            MeetingEventPublisher eventPublisher,
            ClockProvider clockProvider,
            AuthServiceClient authServiceClient,
            WaitingRoomSseService waitingRoomSseService) {
        this.roomRepository = roomRepository;
        this.settingsRepository = settingsRepository;
        this.participantRepository = participantRepository;
        this.recordingRepository = recordingRepository;
        this.tagRepository = tagRepository;
        this.favoriteRepository = favoriteRepository;
        this.invitationRepository = invitationRepository;
        this.liveKitClient = liveKitClient;
        this.liveKitProperties = liveKitProperties;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.authServiceClient = authServiceClient;
        this.waitingRoomSseService = waitingRoomSseService;
    }

    @Transactional
    public MeetingRoomResponse create(RoomCreateRequest request, Long hostUserId) {
        RoomType type = request.type() != null ? request.type() : RoomType.INSTANT;
        int maxParticipants = request.maxParticipants() != null ? request.maxParticipants() : 10;
        RoomAccessScope accessScope = request.accessScope() != null ? request.accessScope() : RoomAccessScope.ALL;

        validateAccessScope(accessScope, request.teamId());

        // Validate team existence and creator's membership when access scope is TEAM
        if (accessScope == RoomAccessScope.TEAM && request.teamId() != null) {
            if (!authServiceClient.teamExists(request.teamId())) {
                // TODO: [VIDEO][VideoErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
                throw new BusinessException(VideoErrorCode.TEAM_NOT_FOUND);
            }
            if (!authServiceClient.isTeamMember(request.teamId(), hostUserId)) {
                // TODO: [VIDEO][VideoErrorCode.HOST_NOT_TEAM_MEMBER] 에러메시지 검수 요청
                throw new BusinessException(VideoErrorCode.HOST_NOT_TEAM_MEMBER);
            }
        }

        MeetingRoom room = new MeetingRoom(
                request.title(),
                request.description(),
                hostUserId,
                type,
                maxParticipants,
                request.password(),
                request.scheduledAtAsInstant(),
                accessScope,
                request.teamId());

        while (roomRepository.existsByRoomCode(room.getRoomCode())) {
            room.regenerateCode();
        }

        MeetingRoom saved = roomRepository.save(room);
        settingsRepository.save(RoomSettings.createDefault(saved));
        liveKitClient.createRoom(saved.getLivekitRoomName(), maxParticipants);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MeetingRoomDetailResponse get(Long roomId) {
        MeetingRoom room = findRoom(roomId);
        RoomSettings settings = settingsRepository.findByRoomId(roomId).orElse(null);
        int participantCount = participantRepository.countActiveParticipants(roomId);

        List<String> tags = tagRepository.findByRoomId(roomId).stream()
                .map(RoomTag::getTagName)
                .collect(Collectors.toList());

        return toDetailResponse(room, participantCount, settings, tags);
    }

    @Transactional(readOnly = true)
    public Page<MeetingRoomResponse> list(RoomStatus status, RoomType type, RoomAccessScope accessScope,
            Long hostUserId, Pageable pageable) {
        return roomRepository.findAllWithFilters(status, type, accessScope, hostUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MeetingRoomResponse update(Long roomId, RoomUpdateRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);
        room.updateInfo(request.title(), request.description(), request.maxParticipants());
        return toResponse(room);
    }

    @Transactional
    public void delete(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (room.isActive()) {
            // TODO: [VIDEO][VideoErrorCode.ACTIVE_ROOM_DELETE_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ACTIVE_ROOM_DELETE_DENIED);
        }

        liveKitClient.deleteRoom(room.getLivekitRoomName());
        roomRepository.delete(room);
    }

    // CHECK [video-담당자]: findByCode 반환 타입 MeetingRoomResponse -> MeetingRoomDetailResponse
    @Transactional(readOnly = true)
    public MeetingRoomDetailResponse findByCode(String roomCode) {
        MeetingRoom room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_CODE_NOT_FOUND));
        RoomSettings settings = settingsRepository.findByRoomId(room.getId()).orElse(null);
        int participantCount = participantRepository.countActiveParticipants(room.getId());
        List<String> tags = tagRepository.findByRoomId(room.getId()).stream()
                .map(RoomTag::getTagName)
                .collect(Collectors.toList());
        return toDetailResponse(room, participantCount, settings, tags);
    }

    @Transactional
    public MeetingRoomResponse regenerateCode(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);
        room.regenerateCode();
        while (roomRepository.existsByRoomCode(room.getRoomCode())) {
            room.regenerateCode();
        }
        return toResponse(room);
    }

    @Transactional
    public RoomJoinResponse join(Long roomId, RoomJoinRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            throw new BusinessException(VideoErrorCode.ROOM_ALREADY_ENDED);
        }

        // 다른 회의에 참여 중이면 자동 퇴장 처리
        List<String> warnings = new ArrayList<>();
        try {
            List<RoomParticipant> activeParticipations = participantRepository.findByUserIdAndStatusIn(
                    userId, List.of(ParticipantStatus.JOINED, ParticipantStatus.WAITING));
            for (RoomParticipant existing : activeParticipations) {
                Long existingRoomId = existing.getRoom().getId();
                boolean wasWaiting = existing.isWaiting();
                Instant leaveTime = clockProvider.now();
                existing.leave(leaveTime);

                try {
                    if (wasWaiting) {
                        waitingRoomSseService.notifyHostWaiterLeft(existingRoomId, userId);
                    } else {
                        liveKitClient.removeParticipant(existing.getRoom().getLivekitRoomName(), String.valueOf(userId));
                    }
                } catch (Exception e) {
                    // LiveKit/SSE 호출 실패해도 퇴장 처리는 진행
                }

                if (!existingRoomId.equals(roomId)) {
                    eventPublisher.publishParticipantLeft(
                            new ParticipantEvent("PARTICIPANT_LEFT", existingRoomId, userId, leaveTime));
                    warnings.add("기존 회의 '" + existing.getRoom().getTitle() + "'에서 자동 퇴장되었습니다.");
                }
            }
        } catch (Exception e) {
            // 자동 퇴장 실패해도 새 방 입장은 계속 진행
        }

        Instant now = clockProvider.now();

        // 예정된 회의 충돌 확인
        try {
            Instant rangeStart = now.minus(Duration.ofMinutes(30));
            Instant rangeEnd = now.plus(Duration.ofMinutes(30));
            if (roomRepository.existsConflictingSchedule(userId, RoomType.SCHEDULED, RoomStatus.WAITING, rangeStart, rangeEnd, roomId)) {
                warnings.add("현재 시간대에 예정된 다른 회의가 있습니다.");
            }
        } catch (Exception e) {
            // 충돌 확인 실패해도 입장은 계속 진행
        }

        // Validate participant's team membership when access scope is TEAM
        if (room.getAccessScope() == RoomAccessScope.TEAM && room.getTeamId() != null && !room.isHost(userId)) {
            if (!authServiceClient.isTeamMember(room.getTeamId(), userId)) {
                throw new BusinessException(VideoErrorCode.NOT_TEAM_MEMBER);
            }
        }

        if (room.isLocked() && !room.isHost(userId)) {
            if (request == null || request.password() == null || !request.password().equals(room.getPassword())) {
                throw new BusinessException(VideoErrorCode.WRONG_PASSWORD);
            }
        }

        int currentCount = participantRepository.countActiveParticipants(roomId);
        if (currentCount >= room.getMaxParticipants()) {
            throw new BusinessException(VideoErrorCode.ROOM_FULL);
        }

        RoomSettings settings = settingsRepository.findByRoomId(roomId).orElse(null);
        boolean isWaitingRoom = settings != null && settings.isWaitingRoom() && !room.isHost(userId);

        DeviceType deviceType = request != null ? request.deviceType() : null;

        ParticipantStatus initialStatus = isWaitingRoom ? ParticipantStatus.WAITING : ParticipantStatus.JOINED;
        ParticipantRole role = room.isHost(userId) ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;

        RoomParticipant participant = new RoomParticipant(room, userId, role, initialStatus, now, deviceType);
        participantRepository.save(participant);

        // 즉시 회의: 호스트가 참여하면 자동으로 WAITING → ACTIVE 전환
        if (room.isWaiting() && room.isHost(userId) && room.getType() == RoomType.INSTANT) {
            room.start(now);
            int participantCount = participantRepository.countActiveParticipants(roomId);
            eventPublisher.publishMeetingStarted(
                    new MeetingEvent("MEETING_STARTED", roomId, userId, participantCount, now, null));
        }

        if (isWaitingRoom) {
            waitingRoomSseService.notifyHostNewWaiter(roomId, toParticipantResponse(participant));
            return new RoomJoinResponse(null, liveKitProperties.getUrl(), room.getLivekitRoomName(), true, warnings);
        }

        // Get user name from auth service (실패해도 입장은 진행)
        String participantName = "user-" + userId;
        try {
            AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(userId);
            if (userInfo != null && userInfo.name() != null) {
                participantName = userInfo.name();
            }
        } catch (Exception e) {
            // auth 서비스 호출 실패 시 기본 이름 사용
        }

        TokenGrants grants = room.isHost(userId) ? TokenGrants.forHost() : TokenGrants.forParticipant();
        String token = liveKitClient.generateToken(
                room.getLivekitRoomName(),
                String.valueOf(userId),
                participantName,
                grants);

        eventPublisher.publishParticipantJoined(
                new ParticipantEvent("PARTICIPANT_JOINED", roomId, userId, now));

        return new RoomJoinResponse(token, liveKitProperties.getUrl(), room.getLivekitRoomName(), false, warnings);
    }

    @Transactional
    public void leave(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatusIn(roomId, userId,
                        List.of(ParticipantStatus.JOINED, ParticipantStatus.WAITING))
                .orElseThrow(() -> new BusinessException(VideoErrorCode.NOT_A_PARTICIPANT));

        boolean wasWaiting = participant.isWaiting();
        Instant now = clockProvider.now();
        participant.leave(now);

        if (wasWaiting) {
            waitingRoomSseService.notifyHostWaiterLeft(roomId, userId);
        }

        liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(userId));

        eventPublisher.publishParticipantLeft(
                new ParticipantEvent("PARTICIPANT_LEFT", roomId, userId, now));
    }

    @Transactional
    public MeetingRoomResponse start(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isWaiting()) {
            // TODO: [VIDEO][VideoErrorCode.ROOM_NOT_WAITING] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ROOM_NOT_WAITING);
        }

        Instant now = clockProvider.now();
        room.start(now);

        int participantCount = participantRepository.countActiveParticipants(roomId);
        eventPublisher.publishMeetingStarted(
                new MeetingEvent("MEETING_STARTED", roomId, userId, participantCount, now, null));

        return toResponse(room);
    }

    @Transactional
    public MeetingRoomResponse end(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isActive()) {
            // TODO: [VIDEO][VideoErrorCode.ROOM_NOT_ACTIVE] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ROOM_NOT_ACTIVE);
        }

        Instant now = clockProvider.now();
        room.end(now);

        List<RoomParticipant> activeParticipants = participantRepository
                .findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED);
        for (RoomParticipant p : activeParticipants) {
            p.leave(now);
            try {
                liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(p.getUserId()));
            } catch (Exception e) {
                log.warn("Failed to remove participant from LiveKit: roomId={}, userId={}, error={}",
                        roomId, p.getUserId(), e.getMessage());
            }
        }

        List<RoomParticipant> waitingParticipants = participantRepository
                .findByRoomIdAndStatus(roomId, ParticipantStatus.WAITING);
        for (RoomParticipant p : waitingParticipants) {
            p.leave(now);
        }

        waitingRoomSseService.cleanupRoom(roomId);

        // 회의 메타데이터(참가자 목록)를 회의록 서비스로 전달
        List<RoomParticipant> allParticipants = participantRepository.findByRoomId(roomId);
        List<Long> participantUserIds = allParticipants.stream()
                .map(RoomParticipant::getUserId).distinct().collect(Collectors.toList());

        Map<Long, String> userNameMap = Map.of();
        if (!participantUserIds.isEmpty()) {
            List<AuthServiceClient.UserInfo> userInfos = authServiceClient.getBatchUserInfo(participantUserIds);
            if (userInfos != null) {
                userNameMap = userInfos.stream()
                        .collect(Collectors.toMap(AuthServiceClient.UserInfo::userId, AuthServiceClient.UserInfo::name, (a, b) -> a));
            }
        }

        Map<Long, String> finalUserNameMap = userNameMap;
        List<MeetingEvent.ParticipantInfo> participantInfos = allParticipants.stream()
                .collect(Collectors.toMap(RoomParticipant::getUserId, p -> p, (a, b) -> a))
                .values().stream()
                .map(p -> new MeetingEvent.ParticipantInfo(
                        p.getUserId(),
                        finalUserNameMap.getOrDefault(p.getUserId(), "user-" + p.getUserId()),
                        p.getRole().name()))
                .collect(Collectors.toList());

        eventPublisher.publishMeetingEnded(
                new MeetingEvent("MEETING_ENDED", roomId, userId, activeParticipants.size(),
                        room.getStartedAt(), now, room.getTitle(), room.getDescription(), participantInfos));

        return toResponse(room);
    }

    @Transactional
    public void lock(Long roomId, RoomLockRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);
        room.lock(request != null ? request.password() : null);
    }

    @Transactional
    public void unlock(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);
        room.unlock();
    }

    @Transactional(readOnly = true)
    public RoomSettingsResponse getSettings(Long roomId) {
        findRoom(roomId);
        RoomSettings settings = settingsRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.SETTINGS_NOT_FOUND));
        return toSettingsResponse(settings);
    }

    @Transactional
    public RoomSettingsResponse updateSettings(Long roomId, RoomSettingsUpdateRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);

        RoomSettings settings = settingsRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.SETTINGS_NOT_FOUND));

        settings.update(
                request.videoEnabled(),
                request.audioEnabled(),
                request.screenShareAllowed(),
                request.chatEnabled(),
                request.recordingEnabled(),
                request.waitingRoom(),
                request.autoMuteOnJoin());

        return toSettingsResponse(settings);
    }

    @Transactional
    public void addTag(Long roomId, TagCreateRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (tagRepository.findByRoomIdAndTagName(roomId, request.tagName()).isPresent()) {
            // TODO: [VIDEO][VideoErrorCode.TAG_ALREADY_EXISTS] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.TAG_ALREADY_EXISTS);
        }

        tagRepository.save(new RoomTag(room, request.tagName()));
    }

    @Transactional
    public void removeTag(Long roomId, String tagName, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        RoomTag tag = tagRepository.findByRoomIdAndTagName(roomId, tagName)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.TAG_NOT_FOUND));

        tagRepository.delete(tag);
    }

    // CHECK [video-담당자]: searchByTag 반환 타입 List -> Page
    @Transactional(readOnly = true)
    public Page<MeetingRoomResponse> searchByTag(String tagName, Pageable pageable) {
        return roomRepository.findByTagName(tagName, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void addFavorite(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        if (favoriteRepository.existsByUserIdAndRoomId(userId, roomId)) {
            // TODO: [VIDEO][VideoErrorCode.FAVORITE_ALREADY_EXISTS] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        favoriteRepository.save(new RoomFavorite(userId, room));
    }

    @Transactional
    public void removeFavorite(Long roomId, Long userId) {
        RoomFavorite favorite = favoriteRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
    }

    // CHECK [video-담당자]: listFavorites 반환 타입 List -> Page
    @Transactional(readOnly = true)
    public Page<MeetingRoomResponse> listFavorites(Long userId, Pageable pageable) {
        return favoriteRepository.findByUserId(userId, pageable)
                .map(fav -> toResponse(fav.getRoom()));
    }

    @Transactional
    public MeetingRoomResponse schedule(RoomScheduleRequest request, Long userId) {
        int maxParticipants = request.maxParticipants() != null ? request.maxParticipants() : 10;
        RoomAccessScope accessScope = request.accessScope() != null ? request.accessScope() : RoomAccessScope.ALL;

        validateAccessScope(accessScope, request.teamId());

        // Validate team existence and creator's membership when access scope is TEAM
        if (accessScope == RoomAccessScope.TEAM && request.teamId() != null) {
            if (!authServiceClient.teamExists(request.teamId())) {
                // TODO: [VIDEO][VideoErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
                throw new BusinessException(VideoErrorCode.TEAM_NOT_FOUND);
            }
            if (!authServiceClient.isTeamMember(request.teamId(), userId)) {
                // TODO: [VIDEO][VideoErrorCode.HOST_NOT_TEAM_MEMBER] 에러메시지 검수 요청
                throw new BusinessException(VideoErrorCode.HOST_NOT_TEAM_MEMBER);
            }
        }

        Instant scheduledAt = request.scheduledAtAsInstant();
        validateNoScheduleConflict(userId, scheduledAt, null);

        MeetingRoom room = new MeetingRoom(
                request.title(),
                request.description(),
                userId,
                RoomType.SCHEDULED,
                maxParticipants,
                request.password(),
                scheduledAt,
                accessScope,
                request.teamId());

        while (roomRepository.existsByRoomCode(room.getRoomCode())) {
            room.regenerateCode();
        }

        MeetingRoom saved = roomRepository.save(room);
        settingsRepository.save(RoomSettings.createDefault(saved));


        return toResponse(saved);
    }

    // CHECK [video-담당자]: listScheduled 반환 타입 List -> Page
    @Transactional(readOnly = true)
    public Page<MeetingRoomResponse> listScheduled(Long userId, Pageable pageable) {
        return roomRepository.findByHostUserIdAndTypeAndStatusNot(userId, RoomType.SCHEDULED, RoomStatus.CANCELLED, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MeetingRoomResponse updateSchedule(Long roomId, Instant scheduledAt, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (room.getType() != RoomType.SCHEDULED) {
            // TODO: [VIDEO][VideoErrorCode.NOT_SCHEDULED_ROOM] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.NOT_SCHEDULED_ROOM);
        }
        if (!room.isWaiting()) {
            // TODO: [VIDEO][VideoErrorCode.SCHEDULE_CHANGE_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCHEDULE_CHANGE_DENIED);
        }
        if (scheduledAt.isBefore(clockProvider.now())) {
            // TODO: [VIDEO][VideoErrorCode.SCHEDULE_PAST_TIME] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCHEDULE_PAST_TIME);
        }

        validateNoScheduleConflict(room.getHostUserId(), scheduledAt, roomId);

        room.updateSchedule(scheduledAt);
        return toResponse(room);
    }

    @Transactional
    public void cancelSchedule(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isWaiting()) {
            // TODO: [VIDEO][VideoErrorCode.CANCEL_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.CANCEL_DENIED);
        }

        room.cancel();
    }

    // CHECK [video-담당자]: listHistory 반환 타입 List -> Page
    @Transactional(readOnly = true)
    public Page<MeetingRoomResponse> listHistory(Long userId, Pageable pageable) {
        return roomRepository.findByHostUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /**
     * 사용자가 관련된 회의 목록 (호스트 + 초대받은 + 참여한).
     * 선택적으로 상태 필터 적용 가능.
     */
    @Transactional(readOnly = true)
    public List<MeetingRoomResponse> listMyRooms(Long userId, RoomStatus status) {
        // 1) 호스트인 방
        List<MeetingRoom> hostRooms = status != null
                ? roomRepository.findAllWithFilters(status, null, null, userId, Pageable.unpaged()).getContent()
                : roomRepository.findByHostUserIdOrderByCreatedAtDesc(userId);

        // 2) 초대받은 방 (PENDING 또는 ACCEPTED)
        List<RoomInvitation> invitations = invitationRepository.findByInviteeUserIdAndStatusIn(
                userId, List.of(InvitationStatus.PENDING, InvitationStatus.ACCEPTED));
        List<MeetingRoom> invitedRooms = invitations.stream()
                .map(RoomInvitation::getRoom)
                .filter(room -> status == null || room.getStatus() == status)
                .collect(Collectors.toList());

        // 3) 참여 이력이 있는 방
        List<RoomParticipant> participations = participantRepository.findByUserIdAndStatusIn(
                userId, List.of(ParticipantStatus.JOINED, ParticipantStatus.WAITING));
        List<MeetingRoom> participatedRooms = participations.stream()
                .map(RoomParticipant::getRoom)
                .filter(room -> status == null || room.getStatus() == status)
                .collect(Collectors.toList());

        // 중복 제거 후 반환
        Map<Long, MeetingRoom> uniqueRooms = new java.util.LinkedHashMap<>();
        for (MeetingRoom r : hostRooms) uniqueRooms.putIfAbsent(r.getId(), r);
        for (MeetingRoom r : invitedRooms) uniqueRooms.putIfAbsent(r.getId(), r);
        for (MeetingRoom r : participatedRooms) uniqueRooms.putIfAbsent(r.getId(), r);

        return uniqueRooms.values().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomStatsResponse getStats(Long roomId) {
        MeetingRoom room = findRoom(roomId);
        int totalParticipants = participantRepository.findByRoomId(roomId).size();
        int currentParticipants = participantRepository.countActiveParticipants(roomId);
        int totalRecordings = recordingRepository.findByRoomId(roomId).size();

        return new RoomStatsResponse(
                room.getId(),
                room.getTitle(),
                room.getStatus(),
                totalParticipants,
                currentParticipants,
                room.getDurationSeconds(),
                totalRecordings,
                room.getStartedAt(),
                room.getEndedAt());
    }

    @Transactional(readOnly = true)
    public List<MonthlyStatsResponse> getMonthlyStats(Long userId) {
        List<MeetingRoom> rooms = roomRepository.findByHostUserIdOrderByCreatedAtDesc(userId);

        Map<YearMonth, List<MeetingRoom>> grouped = rooms.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")))));

        List<MonthlyStatsResponse> results = new ArrayList<>();
        for (Map.Entry<YearMonth, List<MeetingRoom>> entry : grouped.entrySet()) {
            YearMonth ym = entry.getKey();
            List<MeetingRoom> monthRooms = entry.getValue();

            long totalDuration = monthRooms.stream()
                    .filter(r -> r.getDurationSeconds() != null)
                    .mapToLong(MeetingRoom::getDurationSeconds)
                    .sum();

            long totalParticipants = monthRooms.stream()
                    .mapToLong(r -> participantRepository.findByRoomId(r.getId()).size())
                    .sum();

            results.add(new MonthlyStatsResponse(
                    ym.getYear(),
                    ym.getMonthValue(),
                    monthRooms.size(),
                    totalDuration,
                    totalParticipants));
        }

        results.sort((a, b) -> {
            int yearCmp = Integer.compare(b.year(), a.year());
            return yearCmp != 0 ? yearCmp : Integer.compare(b.month(), a.month());
        });

        return results;
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> getTimeline(Long roomId) {
        findRoom(roomId);

        List<TimelineEntry> timeline = new ArrayList<>();

        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);

        // Batch fetch user names for all participants
        List<Long> userIds = participants.stream()
                .map(RoomParticipant::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> userNameMap;
        try {
            List<AuthServiceClient.UserInfo> userInfos = authServiceClient.getBatchUserInfo(userIds);
            userNameMap = userInfos.stream()
                    .collect(Collectors.toMap(
                            AuthServiceClient.UserInfo::userId,
                            AuthServiceClient.UserInfo::name,
                            (a, b) -> a));
        } catch (Exception e) {
            userNameMap = Map.of();
        }

        for (RoomParticipant p : participants) {
            String userName = userNameMap.getOrDefault(p.getUserId(), "User " + p.getUserId());
            timeline.add(new TimelineEntry(
                    "PARTICIPANT_JOINED",
                    p.getUserId(),
                    userName + " joined as " + p.getRole(),
                    p.getJoinedAt()));
            if (p.getLeftAt() != null) {
                timeline.add(new TimelineEntry(
                        p.getStatus().name(),
                        p.getUserId(),
                        userName + " " + p.getStatus().name().toLowerCase(),
                        p.getLeftAt()));
            }
        }

        timeline.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return timeline;
    }

    @Transactional(readOnly = true)
    public void sendReminder(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (room.getType() != RoomType.SCHEDULED) {
            // TODO: [VIDEO][VideoErrorCode.REMINDER_NOT_SCHEDULED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.REMINDER_NOT_SCHEDULED);
        }
        if (room.isEnded()) {
            // TODO: [VIDEO][VideoErrorCode.REMINDER_ROOM_ENDED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.REMINDER_ROOM_ENDED);
        }

        eventPublisher.publishMeetingStarted(
                new MeetingEvent("MEETING_REMINDER", roomId, userId, 0, null, null));
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
    }

    private void validateHost(MeetingRoom room, Long userId) {
        if (!room.isHost(userId)) {
            // TODO: [VIDEO][VideoErrorCode.HOST_ONLY] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.HOST_ONLY);
        }
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
                .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.HOST_OR_COHOST_ONLY));
    }

    private void validateAccessScope(RoomAccessScope accessScope, Long teamId) {
        if (accessScope == RoomAccessScope.TEAM && teamId == null) {
            // TODO: [VIDEO][VideoErrorCode.TEAM_ID_REQUIRED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.TEAM_ID_REQUIRED);
        }
    }

    private void validateNoScheduleConflict(Long hostUserId, Instant scheduledAt, Long excludeRoomId) {
        Instant rangeStart = scheduledAt.minus(Duration.ofMinutes(30));
        Instant rangeEnd = scheduledAt.plus(Duration.ofMinutes(30));

        if (roomRepository.existsConflictingSchedule(
                hostUserId, RoomType.SCHEDULED, RoomStatus.WAITING, rangeStart, rangeEnd, excludeRoomId)) {
            // TODO: [VIDEO][VideoErrorCode.SCHEDULE_CONFLICT] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCHEDULE_CONFLICT);
        }
    }

    private MeetingRoomResponse toResponse(MeetingRoom room) {
        return new MeetingRoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getTitle(),
                room.getDescription(),
                room.getHostUserId(),
                room.getStatus(),
                room.getType(),
                room.getAccessScope(),
                room.getTeamId(),
                room.getMaxParticipants(),
                room.isLocked(),
                room.getScheduledAt(),
                room.getStartedAt(),
                room.getEndedAt(),
                room.getDurationSeconds(),
                room.getCreatedAt());
    }

    private MeetingRoomDetailResponse toDetailResponse(MeetingRoom room, int participantCount,
            RoomSettings settings, List<String> tags) {
        return new MeetingRoomDetailResponse(
                room.getId(),
                room.getRoomCode(),
                room.getTitle(),
                room.getDescription(),
                room.getHostUserId(),
                room.getStatus(),
                room.getType(),
                room.getAccessScope(),
                room.getTeamId(),
                room.getMaxParticipants(),
                room.isLocked(),
                room.getScheduledAt(),
                room.getStartedAt(),
                room.getEndedAt(),
                room.getDurationSeconds(),
                participantCount,
                settings != null ? toSettingsResponse(settings) : null,
                tags,
                room.getCreatedAt());
    }

    private RoomParticipantResponse toParticipantResponse(RoomParticipant p) {
        return new RoomParticipantResponse(
                p.getId(),
                p.getRoom().getId(),
                p.getUserId(),
                p.getRole(),
                p.getStatus(),
                p.getJoinedAt(),
                p.getLeftAt(),
                p.getDurationSeconds(),
                p.getDeviceType());
    }

    private RoomSettingsResponse toSettingsResponse(RoomSettings settings) {
        return new RoomSettingsResponse(
                settings.getId(),
                settings.getRoom().getId(),
                settings.isVideoEnabled(),
                settings.isAudioEnabled(),
                settings.isScreenShareAllowed(),
                settings.isChatEnabled(),
                settings.isRecordingEnabled(),
                settings.isWaitingRoom(),
                settings.isAutoMuteOnJoin());
    }
}
