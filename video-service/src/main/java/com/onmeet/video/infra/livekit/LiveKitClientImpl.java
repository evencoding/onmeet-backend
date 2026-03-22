package com.onmeet.video.infra.livekit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * LiveKit 서버와 실제 통신하는 구현체.
 * 로컬 환경을 제외한 프로파일(chat, dev, prod)에서 활성화된다.
 *
 * 토큰 생성: 표준 Java HMAC-SHA256 (javax.crypto.Mac) 사용 — 별도 JWT 라이브러리 불필요.
 * 서버 API: LiveKit REST/Twirp API (JSON 페이로드)
 */
// CHECK [video-담당자]: LiveKitClientImpl - LiveKit 서버 URL(livekit.url), API Key, Secret이
// 환경변수(LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET)로 올바르게 주입되는지 확인 필요.
// CHECK [video-담당자]: startTrackEgress - S3 경로 형식이 LiveKit Egress 설정과 일치하는지,
// S3 버킷 권한이 LiveKit 서버에서 쓰기 가능한지 확인 필요.
// CHECK [video-담당자]: 실제 LiveKit 연동은 livekit.enabled=true 설정 시에만 활성화
@Component
@ConditionalOnProperty(name = "livekit.enabled", havingValue = "true")
public class LiveKitClientImpl implements LiveKitClient {

    private static final Logger log = LoggerFactory.getLogger(LiveKitClientImpl.class);
    private static final long TOKEN_TTL_SECONDS = 3600L;
    private static final long ADMIN_TOKEN_TTL_SECONDS = 60L;

    private final LiveKitProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LiveKitClientImpl(LiveKitProperties properties,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createRoom(String roomName, int maxParticipants) {
        Map<String, Object> request = Map.of(
                "name", roomName,
                "max_participants", maxParticipants
        );
        callLiveKit("/twirp/livekit.RoomService/CreateRoom", request);
        log.info("LiveKit room created: name={}, maxParticipants={}", roomName, maxParticipants);
    }

    @Override
    public void deleteRoom(String roomName) {
        Map<String, Object> request = Map.of("room", roomName);
        callLiveKit("/twirp/livekit.RoomService/DeleteRoom", request);
        log.info("LiveKit room deleted: name={}", roomName);
    }

    @Override
    public String generateToken(String roomName, String identity, String participantName, TokenGrants grants) {
        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", grants.canPublish());
        videoGrant.put("canSubscribe", grants.canSubscribe());
        videoGrant.put("canPublishData", grants.canPublishData());
        videoGrant.put("roomAdmin", grants.roomAdmin());
        videoGrant.put("hidden", grants.hidden());

        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", properties.getApiKey());
        claims.put("sub", identity);
        claims.put("iat", now);
        claims.put("nbf", now);
        claims.put("exp", now + TOKEN_TTL_SECONDS);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("video", videoGrant);
        claims.put("metadata", "");
        claims.put("name", participantName);

        return buildJwt(claims, properties.getApiSecret());
    }

    @Override
    public void removeParticipant(String roomName, String identity) {
        Map<String, Object> request = Map.of("room", roomName, "identity", identity);
        callLiveKit("/twirp/livekit.RoomService/RemoveParticipant", request);
    }

    @Override
    public void muteParticipantTrack(String roomName, String identity, String trackSid, boolean muted) {
        Map<String, Object> request = Map.of(
                "room", roomName,
                "identity", identity,
                "track_sid", trackSid,
                "muted", muted
        );
        callLiveKit("/twirp/livekit.RoomService/MutePublishedTrack", request);
    }

    // CHECK [video-담당자]: startRoomCompositeEgress 미구현 — S3 자격증명(access_key, secret, bucket) 및
    // layout 파라미터가 확정되면 실제 구현 필요. 현재 recording 서비스는 startTrackEgress만 사용하므로 즉각 장애 없음.
    @Override
    public String startRoomCompositeEgress(String roomName, String s3Path) {
        throw new UnsupportedOperationException(
                "startRoomCompositeEgress is not yet implemented. S3 credentials and layout config required.");
    }

    // CHECK [video-담당자]: startTrackCompositeEgress 미구현 — audioTrackId/videoTrackId 조회 로직 및
    // 세그먼트 설정이 확정되면 실제 구현 필요. 현재 recording 서비스는 startTrackEgress만 사용하므로 즉각 장애 없음.
    @Override
    public String startTrackCompositeEgress(String roomName, String s3Path, int segmentDurationSeconds) {
        throw new UnsupportedOperationException(
                "startTrackCompositeEgress is not yet implemented. Track ID resolution logic required.");
    }

    @Override
    public String startTrackEgress(String roomName, String trackSid, String s3Path) {
        // CHECK [video-담당자]: S3 경로 형식과 AWS 접근 키가 LiveKit 서버에서 사용 가능한 값인지 확인 필요.
        // LiveKit Egress S3 설정은 별도 egress.yaml 또는 environment로 주입되어야 함.
        Map<String, Object> s3Output = Map.of("filepath", s3Path);
        Map<String, Object> request = Map.of(
                "room_name", roomName,
                "track_id", trackSid,
                "file", s3Output
        );
        EgressResponse response = callLiveKit("/twirp/livekit.EgressService/StartTrackEgress",
                request, EgressResponse.class);
        String egressId = response != null ? response.egressId : "egress_" + UUID.randomUUID();
        log.info("LiveKit track egress started: room={}, trackSid={}, egressId={}", roomName, trackSid, egressId);
        return egressId;
    }

    @Override
    public List<ParticipantInfo> listParticipants(String roomName) {
        Map<String, Object> request = Map.of("room", roomName);
        ListParticipantsResponse response = callLiveKit(
                "/twirp/livekit.RoomService/ListParticipants", request, ListParticipantsResponse.class);
        if (response == null || response.participants == null) {
            return Collections.emptyList();
        }
        return response.participants.stream()
                .map(p -> new ParticipantInfo(
                        p.identity,
                        p.name,
                        p.tracks != null
                                ? p.tracks.stream()
                                .map(t -> new TrackInfo(t.sid, t.source))
                                .toList()
                                : List.of()
                ))
                .toList();
    }

    @Override
    public void stopEgress(String egressId) {
        Map<String, Object> request = Map.of("egress_id", egressId);
        callLiveKit("/twirp/livekit.EgressService/StopEgress", request);
        log.info("LiveKit egress stopped: egressId={}", egressId);
    }

    @Override
    public void publishData(String roomName, byte[] data, DataPacketKind kind) {
        publishData(roomName, data, kind, null);
    }

    @Override
    public void publishData(String roomName, byte[] data, DataPacketKind kind, String destinationIdentity) {
        Map<String, Object> request = new HashMap<>();
        request.put("room", roomName);
        request.put("data", Base64.getEncoder().encodeToString(data));
        request.put("kind", kind == DataPacketKind.RELIABLE ? 1 : 0);
        if (destinationIdentity != null) {
            request.put("destination_sids", List.of(destinationIdentity));
        }
        callLiveKit("/twirp/livekit.RoomService/SendData", request);
    }

    // --- JWT helpers (standard Java HMAC-SHA256, no external library) ---

    // CHECK [video-담당자]: JWT 생성 로직 - HS256 서명에 사용되는 secret은 LiveKit API Secret과 동일해야 함.
    private String buildJwt(Map<String, Object> payload, String secret) {
        try {
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payloadJson = objectMapper.writeValueAsString(payload);

            String headerEncoded = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = headerEncoded + "." + payloadEncoded;

            byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8),
                    secret.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64UrlEncode(signature);
        } catch (Exception e) {
            throw new RuntimeException("LiveKit JWT generation failed", e);
        }
    }

    private String generateAdminToken() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", properties.getApiKey());
        claims.put("sub", "admin");
        claims.put("iat", now);
        claims.put("nbf", now);
        claims.put("exp", now + ADMIN_TOKEN_TTL_SECONDS);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("video", Map.of("roomCreate", true, "roomList", true, "roomAdmin", true));
        return buildJwt(claims, properties.getApiSecret());
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // --- LiveKit REST helpers ---

    private void callLiveKit(String path, Object requestBody) {
        callLiveKit(path, requestBody, Void.class);
    }

    private <T> T callLiveKit(String path, Object requestBody, Class<T> responseType) {
        String url = buildUrl(path);
        String adminToken = generateAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            return restTemplate.postForObject(url, entity, responseType);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize LiveKit request: path={}, error={}", path, e.getMessage());
            throw new RuntimeException("LiveKit request serialization failed", e);
        } catch (Exception e) {
            log.error("LiveKit API call failed: path={}, error={}", path, e.getMessage());
            throw new RuntimeException("LiveKit API call failed: " + path, e);
        }
    }

    private String buildUrl(String path) {
        String baseUrl = properties.getUrl()
                .replace("ws://", "http://")
                .replace("wss://", "https://");
        return baseUrl + path;
    }

    // --- Internal response DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EgressResponse {
        @JsonProperty("egress_id")
        String egressId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ListParticipantsResponse {
        @JsonProperty("participants")
        List<ParticipantData> participants;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ParticipantData {
        @JsonProperty("identity")
        String identity;
        @JsonProperty("name")
        String name;
        @JsonProperty("tracks")
        List<TrackData> tracks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TrackData {
        @JsonProperty("sid")
        String sid;
        @JsonProperty("source")
        String source;
    }
}
