package com.onmeet.ai.pipeline.transcript;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TranscriptRenderer {

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
            String actor = (e.getActorId() == null || e.getActorId().isBlank()) ? "unknown" : e.getActorId();
            String type = (e.getType() == null) ? "UNKNOWN" : e.getType().toUpperCase();

            // 원하는 포맷으로 조정 가능
            if ("VOICE".equals(type)) {
                sb.append("[").append(type).append("]")
                        .append(" (").append(e.getStartMs()).append("~").append(e.getEndMs()).append(")")
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
        if (e.getAtMs() != null) return e.getAtMs();
        if (e.getStartMs() != null) return e.getStartMs();
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
