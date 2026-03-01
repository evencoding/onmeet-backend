package com.onmeet.video.meeting.service;

import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.external.AuthServiceClient;
import com.onmeet.video.infra.external.NotificationServiceClient;
import com.onmeet.video.infra.external.LiveKitClient;
import com.onmeet.video.infra.external.LiveKitClient.TokenGrants;
import com.onmeet.video.meeting.config.LiveKitProperties;
import com.onmeet.video.meeting.dto.MeetingRoomDetailResponse;
import com.onmeet.video.meeting.dto.MeetingRoomResponse;
import com.onmeet.video.meeting.dto.MonthlyStatsResponse;
import com.onmeet.video.meeting.dto.RoomCreateRequest;
import com.onmeet.video.meeting.dto.RoomJoinRequest;
import com.onmeet.video.meeting.dto.RoomJoinResponse;
import com.onmeet.video.meeting.dto.RoomLockRequest;
import com.onmeet.video.meeting.dto.RoomScheduleRequest;
import com.onmeet.video.meeting.dto.RoomSettingsResponse;
import com.onmeet.video.meeting.dto.RoomSettingsUpdateRequest;
import com.onmeet.video.meeting.dto.RoomStatsResponse;
import com.onmeet.video.meeting.dto.RoomUpdateRequest;
import com.onmeet.video.meeting.dto.TagCreateRequest;
import com.onmeet.video.meeting.dto.TimelineEntry;
import com.onmeet.video.meeting.entity.DeviceType;
import com.onmeet.video.meeting.entity.MeetingRoom;
import com.onmeet.video.meeting.entity.ParticipantRole;
import com.onmeet.video.meeting.entity.ParticipantStatus;
import com.onmeet.video.meeting.entity.RoomFavorite;
import com.onmeet.video.meeting.entity.RoomParticipant;
import com.onmeet.video.meeting.entity.RoomSettings;
import com.onmeet.video.meeting.entity.RoomStatus;
import com.onmeet.video.meeting.entity.RoomTag;
import com.onmeet.video.meeting.entity.RoomAccessScope;
import com.onmeet.video.meeting.entity.RoomType;
import com.onmeet.video.meeting.event.MeetingEvent;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.ParticipantEvent;
import com.onmeet.video.meeting.repository.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.RoomFavoriteRepository;
import com.onmeet.video.meeting.repository.RoomInvitationRepository;
import com.onmeet.video.meeting.repository.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.RoomSettingsRepository;
import com.onmeet.video.meeting.repository.RoomTagRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingRoomService {

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
    private final NotificationServiceClient notificationClient;

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
            NotificationServiceClient notificationClient) {
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
        this.notificationClient = notificationClient;
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
                throw new BizException(ErrorCode.NOT_FOUND, "Team not found: " + request.teamId());
            }
            if (!authServiceClient.isTeamMember(request.teamId(), hostUserId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Host must be a member of the team");
            }
        }

        MeetingRoom room = new MeetingRoom(
                request.title(),
                request.description(),
                hostUserId,
                type,
                maxParticipants,
                request.password(),
                request.scheduledAt(),
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
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot delete an active room");
        }

        liveKitClient.deleteRoom(room.getLivekitRoomName());
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public MeetingRoomResponse findByCode(String roomCode) {
        MeetingRoom room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found with code: " + roomCode));
        return toResponse(room);
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
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room has already ended");
        }

        boolean alreadyJoined = participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                roomId, userId, List.of(ParticipantStatus.JOINED, ParticipantStatus.WAITING));
        if (alreadyJoined) {
            throw new BizException(ErrorCode.CONFLICT, "Already joined this room");
        }

        // Validate participant's team membership when access scope is TEAM
        if (room.getAccessScope() == RoomAccessScope.TEAM && room.getTeamId() != null && !room.isHost(userId)) {
            if (!authServiceClient.isTeamMember(room.getTeamId(), userId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Only team members can join this room");
            }
        }

        if (room.isLocked() && !room.isHost(userId)) {
            if (request == null || request.password() == null || !request.password().equals(room.getPassword())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Incorrect room password");
            }
        }

        int currentCount = participantRepository.countActiveParticipants(roomId);
        if (currentCount >= room.getMaxParticipants()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room is full");
        }

        RoomSettings settings = settingsRepository.findByRoomId(roomId).orElse(null);
        boolean isWaitingRoom = settings != null && settings.isWaitingRoom() && !room.isHost(userId);

        DeviceType deviceType = request != null ? request.deviceType() : null;
        Instant now = clockProvider.now();

        ParticipantStatus initialStatus = isWaitingRoom ? ParticipantStatus.WAITING : ParticipantStatus.JOINED;
        ParticipantRole role = room.isHost(userId) ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;

        RoomParticipant participant = new RoomParticipant(room, userId, role, initialStatus, now, deviceType);
        participantRepository.save(participant);

        if (isWaitingRoom) {
            return new RoomJoinResponse(null, liveKitProperties.getUrl(), room.getLivekitRoomName(), true);
        }

        // Get user name from auth service
        AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(userId);
        String participantName = userInfo != null ? userInfo.name() : "user-" + userId;

        TokenGrants grants = room.isHost(userId) ? TokenGrants.forHost() : TokenGrants.forParticipant();
        String token = liveKitClient.generateToken(
                room.getLivekitRoomName(),
                String.valueOf(userId),
                participantName,
                grants);

        // 호스트/코호스트에게 새 참가자 입장 알림
        if (!room.isHost(userId)) {
            notificationClient.sendNotification(
                    room.getHostUserId(), "PARTICIPANT_JOINED_NOTIFY",
                    "참가자 입장",
                    participantName + "님이 " + room.getTitle() + " 회의에 참가했습니다.",
                    "/meeting/" + roomId,
                    userId, "MEETING", String.valueOf(roomId));
        }

        eventPublisher.publishParticipantJoined(
                new ParticipantEvent("PARTICIPANT_JOINED", roomId, userId, now));

        return new RoomJoinResponse(token, liveKitProperties.getUrl(), room.getLivekitRoomName(), false);
    }

    @Transactional
    public void leave(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatusIn(roomId, userId,
                        List.of(ParticipantStatus.JOINED, ParticipantStatus.WAITING))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Not a participant of this room"));

        Instant now = clockProvider.now();
        participant.leave(now);

        liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(userId));

        eventPublisher.publishParticipantLeft(
                new ParticipantEvent("PARTICIPANT_LEFT", roomId, userId, now));
    }

    @Transactional
    public MeetingRoomResponse start(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isWaiting()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room can only be started from WAITING status");
        }

        Instant now = clockProvider.now();
        room.start(now);

        int participantCount = participantRepository.countActiveParticipants(roomId);

        // 참가자들에게 회의 시작 알림
        List<RoomParticipant> joinedParticipants = participantRepository.findByRoomIdAndStatus(roomId,
                ParticipantStatus.JOINED);
        for (RoomParticipant p : joinedParticipants) {
            if (!room.isHost(p.getUserId())) {
                notificationClient.sendNotification(
                        p.getUserId(), "MEETING_STARTED",
                        "회의 시작",
                        room.getTitle() + " 회의가 시작되었습니다.",
                        "/meeting/" + roomId,
                        userId, "MEETING", String.valueOf(roomId));
            }
        }

        eventPublisher.publishMeetingStarted(
                new MeetingEvent("MEETING_STARTED", roomId, userId, participantCount, now, null));

        return toResponse(room);
    }

    @Transactional
    public MeetingRoomResponse end(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isActive()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room is not active");
        }

        Instant now = clockProvider.now();
        room.end(now);

        List<RoomParticipant> activeParticipants = participantRepository
                .findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED);
        for (RoomParticipant p : activeParticipants) {
            p.leave(now);
            liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(p.getUserId()));
        }

        List<RoomParticipant> waitingParticipants = participantRepository
                .findByRoomIdAndStatus(roomId, ParticipantStatus.WAITING);
        for (RoomParticipant p : waitingParticipants) {
            p.leave(now);
        }

        // 참가자들에게 회의 종료 알림
        for (RoomParticipant p : activeParticipants) {
            if (!room.isHost(p.getUserId())) {
                notificationClient.sendNotification(
                        p.getUserId(), "SYSTEM",
                        "회의 종료",
                        room.getTitle() + " 회의가 종료되었습니다.",
                        "/meeting/" + roomId,
                        userId, "MEETING", String.valueOf(roomId));
            }
        }

        // TODO: [Minutes Service] 회의 메타데이터(참가자, 시간, 녹음 등)를 회의록 서비스로 전달
        eventPublisher.publishMeetingEnded(
                new MeetingEvent("MEETING_ENDED", roomId, userId, activeParticipants.size(),
                        room.getStartedAt(), now));

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
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room settings not found"));
        return toSettingsResponse(settings);
    }

    @Transactional
    public RoomSettingsResponse updateSettings(Long roomId, RoomSettingsUpdateRequest request, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);

        RoomSettings settings = settingsRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room settings not found"));

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
            throw new BizException(ErrorCode.CONFLICT, "Tag already exists");
        }

        tagRepository.save(new RoomTag(room, request.tagName()));
    }

    @Transactional
    public void removeTag(Long roomId, String tagName, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        RoomTag tag = tagRepository.findByRoomIdAndTagName(roomId, tagName)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Tag not found"));

        tagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<MeetingRoomResponse> searchByTag(String tagName) {
        List<Long> roomIds = tagRepository.findRoomIdsByTagName(tagName);
        return roomRepository.findAllById(roomIds).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addFavorite(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        if (favoriteRepository.existsByUserIdAndRoomId(userId, roomId)) {
            throw new BizException(ErrorCode.CONFLICT, "Already favorited");
        }
        favoriteRepository.save(new RoomFavorite(userId, room));
    }

    @Transactional
    public void removeFavorite(Long roomId, Long userId) {
        RoomFavorite favorite = favoriteRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Favorite not found"));
        favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public List<MeetingRoomResponse> listFavorites(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(fav -> toResponse(fav.getRoom()))
                .collect(Collectors.toList());
    }

    @Transactional
    public MeetingRoomResponse schedule(RoomScheduleRequest request, Long userId) {
        int maxParticipants = request.maxParticipants() != null ? request.maxParticipants() : 10;
        RoomAccessScope accessScope = request.accessScope() != null ? request.accessScope() : RoomAccessScope.ALL;

        validateAccessScope(accessScope, request.teamId());

        // Validate team existence and creator's membership when access scope is TEAM
        if (accessScope == RoomAccessScope.TEAM && request.teamId() != null) {
            if (!authServiceClient.teamExists(request.teamId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "Team not found: " + request.teamId());
            }
            if (!authServiceClient.isTeamMember(request.teamId(), userId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "Host must be a member of the team");
            }
        }

        validateNoScheduleConflict(userId, request.scheduledAt(), null);

        MeetingRoom room = new MeetingRoom(
                request.title(),
                request.description(),
                userId,
                RoomType.SCHEDULED,
                maxParticipants,
                request.password(),
                request.scheduledAt(),
                accessScope,
                request.teamId());

        while (roomRepository.existsByRoomCode(room.getRoomCode())) {
            room.regenerateCode();
        }

        MeetingRoom saved = roomRepository.save(room);
        settingsRepository.save(RoomSettings.createDefault(saved));

        // 예약 회의 생성 시 — 현재는 초대 대상자가 별도로 지정되지 않으므로 생성 이벤트만 기록
        // 초대 API를 통해 사용자가 추가되면 RoomInvitationService에서 알림 발송

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MeetingRoomResponse> listScheduled(Long userId) {
        return roomRepository.findByHostUserIdAndTypeAndStatusNot(userId, RoomType.SCHEDULED, RoomStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MeetingRoomResponse updateSchedule(Long roomId, Instant scheduledAt, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (room.getType() != RoomType.SCHEDULED) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Only scheduled rooms can update schedule");
        }
        if (!room.isWaiting()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot update schedule of a started or ended room");
        }
        if (scheduledAt.isBefore(clockProvider.now())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Scheduled time must be in the future");
        }

        validateNoScheduleConflict(room.getHostUserId(), scheduledAt, roomId);

        room.updateSchedule(scheduledAt);
        // 초대된 참가자들에게 일정 변경 알림
        invitationRepository.findByRoomId(roomId).forEach(inv -> notificationClient.sendNotification(
                inv.getInviteeUserId(), "SCHEDULE_CHANGED",
                "일정 변경",
                room.getTitle() + " 회의 일정이 변경되었습니다.",
                "/meeting/" + roomId,
                userId, "MEETING", String.valueOf(roomId)));
        return toResponse(room);
    }

    @Transactional
    public void cancelSchedule(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHost(room, userId);

        if (!room.isWaiting()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot cancel a started or ended room");
        }

        room.cancel();
        // 초대된 참가자들에게 일정 취소 알림
        invitationRepository.findByRoomId(roomId).forEach(inv -> notificationClient.sendNotification(
                inv.getInviteeUserId(), "SCHEDULE_CANCELLED",
                "일정 취소",
                room.getTitle() + " 회의가 취소되었습니다.",
                "/meeting/" + roomId,
                userId, "MEETING", String.valueOf(roomId)));
    }

    @Transactional(readOnly = true)
    public List<MeetingRoomResponse> listHistory(Long userId) {
        return roomRepository.findByHostUserIdOrderByCreatedAtDesc(userId).stream()
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

        // TODO: [User Service] userId로 실제 사용자 이름 조회하여 타임라인 설명에 표시
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        for (RoomParticipant p : participants) {
            timeline.add(new TimelineEntry(
                    "PARTICIPANT_JOINED",
                    p.getUserId(),
                    "User " + p.getUserId() + " joined as " + p.getRole(),
                    p.getJoinedAt()));
            if (p.getLeftAt() != null) {
                timeline.add(new TimelineEntry(
                        p.getStatus().name(),
                        p.getUserId(),
                        "User " + p.getUserId() + " " + p.getStatus().name().toLowerCase(),
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
            throw new BizException(ErrorCode.INVALID_REQUEST, "Only scheduled rooms can send reminders");
        }
        if (room.isEnded()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot send reminder for an ended room");
        }

        // 초대된 참가자들에게 예약 회의 리마인더 알림
        invitationRepository.findByRoomId(roomId).forEach(inv -> notificationClient.sendNotification(
                inv.getInviteeUserId(), "MEETING_REMINDER",
                "회의 리마인더",
                room.getTitle() + " 회의가 곧 시작됩니다.",
                "/meeting/" + roomId,
                userId, "MEETING", String.valueOf(roomId)));

        eventPublisher.publishMeetingStarted(
                new MeetingEvent("MEETING_REMINDER", roomId, userId, 0, null, null));
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
    }

    private void validateHost(MeetingRoom room, Long userId) {
        if (!room.isHost(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Only the host can perform this action");
        }
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
                .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
                .orElseThrow(
                        () -> new BizException(ErrorCode.FORBIDDEN, "Only host or co-host can perform this action"));
    }

    private void validateAccessScope(RoomAccessScope accessScope, Long teamId) {
        if (accessScope == RoomAccessScope.TEAM && teamId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "teamId is required when accessScope is TEAM");
        }
    }

    private void validateNoScheduleConflict(Long hostUserId, Instant scheduledAt, Long excludeRoomId) {
        Instant rangeStart = scheduledAt.minus(Duration.ofMinutes(30));
        Instant rangeEnd = scheduledAt.plus(Duration.ofMinutes(30));

        if (roomRepository.existsConflictingSchedule(
                hostUserId, RoomType.SCHEDULED, RoomStatus.WAITING, rangeStart, rangeEnd, excludeRoomId)) {
            throw new BizException(ErrorCode.CONFLICT, "Another meeting is already scheduled within this time range");
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
