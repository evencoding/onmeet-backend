package com.onmeet.ai.pipeline.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RedisMeetingEventStore {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisMeetingEventStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    private String eventsKey(String meetingId) {
        return "mt:" + meetingId + ":events";
    }

    private String dedupKey(String meetingId) {
        return "mt:" + meetingId + ":dedup";
    }

    private String padSeq(long seq) {
        return String.format("%020d", seq);
    }

    public void appendChat(ChatMessageEvent e) {
        // dedup: messageId
        String dKey = dedupKey(e.getMeetingId());
        Long added = redis.opsForSet().add(dKey, "CHAT:" + e.getMessageId());
        boolean isNew = (added != null && added > 0);
        if (!isNew) {
            return;
        }

        try {
            String json = om.writeValueAsString(e);
            String member = padSeq(e.getSeq()) + "|CHAT|" + e.getMessageId() + "|" + e.getSenderId() + "|" + json;

            redis.opsForZSet().add(eventsKey(e.getMeetingId()), member, e.getAtMs());
            touchTtl(e.getMeetingId());
        } catch (Exception ex) {
            // dedup set 롤백은 선택(여기선 단순 throw)
            throw new IllegalStateException("failed to append chat to redis", ex);
        }
    }

    public void appendVoice(VoiceSegmentCreatedEvent e) {
        // dedup: segmentId
        String dKey = dedupKey(e.getMeetingId());
        Long added = redis.opsForSet().add(dKey, "VOICE:" + e.getSegmentId());
        boolean isNew = (added != null && added > 0);
        if (!isNew) {
            return; // 중복이면 스킵
        }

        try {
            String json = om.writeValueAsString(e);
            long atMs = e.getStartMs(); // voice는 startMs 기준 정렬
            String member = padSeq(e.getSeq()) + "|VOICE|" + e.getSegmentId() + "|" + e.getParticipantId() + "|" + json;

            redis.opsForZSet().add(eventsKey(e.getMeetingId()), member, atMs);
            touchTtl(e.getMeetingId());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to append voice to redis", ex);
        }
    }

    public List<StoredEvent> readAll(String meetingId) {
        Set<String> members = redis.opsForZSet().range(eventsKey(meetingId), 0, -1);
        if (members == null || members.isEmpty()) return List.of();

        List<StoredEvent> out = new ArrayList<>(members.size());
        for (String m : members) {
            // member: seqPad|TYPE|id|actorId|json
            int p1 = m.indexOf('|');
            int p2 = m.indexOf('|', p1 + 1);
            int p3 = m.indexOf('|', p2 + 1);
            int p4 = m.indexOf('|', p3 + 1);

            String seqPad = m.substring(0, p1);
            String type = m.substring(p1 + 1, p2);
            String id = m.substring(p2 + 1, p3);
            String actorId = m.substring(p3 + 1, p4);
            String json = m.substring(p4 + 1);

            long seq = Long.parseLong(seqPad);
            out.add(new StoredEvent(type, id, actorId, seq, json));
        }
        return out;
    }

    public void clearMeeting(String meetingId) {
        redis.delete(eventsKey(meetingId));
        redis.delete(dedupKey(meetingId));
    }

    private void touchTtl(String meetingId) {
        redis.expire(eventsKey(meetingId), DEFAULT_TTL);
        redis.expire(dedupKey(meetingId), DEFAULT_TTL);
    }

    @Getter
    @AllArgsConstructor
    public static class StoredEvent {
        private String type;   // CHAT|VOICE
        private String id;
        private String actorId;
        private long seq;
        private String json;
    }
}
