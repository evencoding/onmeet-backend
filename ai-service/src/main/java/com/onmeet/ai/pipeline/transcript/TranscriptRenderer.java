package com.onmeet.ai.pipeline.transcript;

import com.onmeet.ai.entity.TranscriptEvent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TranscriptRenderer {

    /**
     * DB의 TranscriptEvent 목록을 줄글(Plain Text)로 변환합니다. (ONMEET-58 신규)
     */
    public String toPlainText(List<TranscriptEvent> events) {
        if (events == null || events.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(events.size() * 32);
        for (TranscriptEvent e : events) {
            if (e.getText() == null || e.getText().isBlank()) continue;
            String actor = (e.getParticipantName() == null || e.getParticipantName().isBlank()) ? "unknown" : e.getParticipantName();
            String type = (e.getType() == null) ? "UNKNOWN" : e.getType().toUpperCase();

            if ("VOICE".equals(type)) {
                sb.append("[").append(type).append("]")
                        .append(" (").append(e.getSegmentStartMs()).append("~").append(e.getSegmentEndMs()).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            } else if ("CHAT".equals(type)) {
                long atMs = e.getTimestamp() != null ? e.getTimestamp().toEpochMilli() : 0L;
                sb.append("[").append(type).append("]")
                        .append(" (").append(atMs).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            } else {
                long atMs = e.getTimestamp() != null ? e.getTimestamp().toEpochMilli() : 0L;
                sb.append("[").append(type).append("]")
                        .append(" (").append(atMs).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * TranscriptDocument 객체를 줄글(Plain Text)로 변환합니다. (하위 호환 유지)
     */
    public String toPlainText(TranscriptDocument doc) {
        if (doc == null || doc.getEvents() == null) return "";

        List<TranscriptDocument.Event> sorted = doc.getEvents().stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getText() != null && !e.getText().isBlank())
                .sorted(eventComparator())
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder(sorted.size() * 32);
        for (TranscriptDocument.Event e : sorted) {
            long at = resolveAtMs(e);
            String actor = (e.getParticipantName() == null || e.getParticipantName().isBlank()) ? 
                    ((e.getParticipantId() == null || e.getParticipantId().isBlank()) ? "unknown" : e.getParticipantId()) 
                    : e.getParticipantName();
            String type = (e.getType() == null) ? "UNKNOWN" : e.getType().toUpperCase();

            if ("VOICE".equals(type)) {
                sb.append("[").append(type).append("]")
                        .append(" (").append(e.getSegmentStartMs()).append("~").append(e.getSegmentEndMs()).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            } else if ("CHAT".equals(type)) {
                sb.append("[").append(type).append("]")
                        .append(" (").append(at).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            } else {
                sb.append("[").append(type).append("]")
                        .append(" (").append(at).append(")")
                        .append(" ").append(actor).append(": ")
                        .append(e.getText().trim())
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private long resolveAtMs(TranscriptDocument.Event e) {
        if (e.getTimestamp() != null) return e.getTimestamp().toEpochMilli();
        if (e.getSegmentStartMs() != null) return e.getSegmentStartMs();
        return 0L;
    }

    private Comparator<TranscriptDocument.Event> eventComparator() {
        return Comparator
                .comparingLong(this::resolveAtMs)
                .thenComparingLong(e -> e.getSeq() == null ? 0 : e.getSeq())
                .thenComparing(e -> e.getType() == null ? "" : e.getType())
                .thenComparing(e -> e.getId() == null ? "" : e.getId());
    }
}
