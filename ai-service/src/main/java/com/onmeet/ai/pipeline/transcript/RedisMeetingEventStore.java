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

    private String eventsKey(Long roomId) {
        return "mt:" + roomId + ":events";
    }

    private String dedupKey(Long roomId) {
        return "mt:" + roomId + ":dedup";
    }

    private String padSeq(long seq) {
        return String.format("%020d", seq);
    }

    public void appendChat(ChatMessageEvent e) {
        String dKey = dedupKey(e.getRoomId());
        Long added = redis.opsForSet().add(dKey, "CHAT:" + e.getMessageId());
        boolean isNew = (added != null && added > 0);
        if (!isNew) {
            return;
        }

        try {
            String json = om.writeValueAsString(e);
            String member = padSeq(e.getSeq()) + "|CHAT|" + e.getMessageId() + "|" + e.getSenderId() + "|" + json;

            // Redis SortedSet score requires double (using epochMilli)
            redis.opsForZSet().add(eventsKey(e.getRoomId()), member, (double) e.getTimestamp().toEpochMilli());
            touchTtl(e.getRoomId());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to append chat to redis", ex);
        }
    }

    public void appendVoice(VoiceSegmentCreatedEvent e) {
        String dKey = dedupKey(e.getRoomId());
        Long added = redis.opsForSet().add(dKey, "VOICE:" + e.getSegmentId());
        boolean isNew = (added != null && added > 0);
        if (!isNew) {
            return;
        }

        try {
            String json = om.writeValueAsString(e);
            // using timestamp for score, or fallback to startMs
            long score = e.getTimestamp() != null ? e.getTimestamp().toEpochMilli() : e.getStartMs();
            String member = padSeq(e.getSeq()) + "|VOICE|" + e.getSegmentId() + "|" + e.getUserId() + "|" + json;

            redis.opsForZSet().add(eventsKey(e.getRoomId()), member, (double) score);
            touchTtl(e.getRoomId());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to append voice to redis", ex);
        }
    }

    public List<StoredEvent> readAll(Long roomId) {
        Set<String> members = redis.opsForZSet().range(eventsKey(roomId), 0, -1);
        if (members == null || members.isEmpty()) return List.of();

        List<StoredEvent> out = new ArrayList<>(members.size());
        for (String m : members) {
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

    public void clearMeeting(Long roomId) {
        redis.delete(eventsKey(roomId));
        redis.delete(dedupKey(roomId));
    }

    private void touchTtl(Long roomId) {
        redis.expire(eventsKey(roomId), DEFAULT_TTL);
        redis.expire(dedupKey(roomId), DEFAULT_TTL);
    }

    @Getter
    @AllArgsConstructor
    public static class StoredEvent {
        private String type;
        private String id;
        private String actorId;
        private long seq;
        private String json;
    }
}
