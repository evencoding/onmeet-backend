package com.onmeet.ai.pipeline.storage;

public final class StorageKeyFactory {

    private StorageKeyFactory() {}

    // transcript는 ai-server가 "finalize" 시 생성해서 올린다고 가정
    public static String transcriptKey(String meetingId, String transcriptId) {
        return "transcripts/" + meetingId + "/" + transcriptId + ".json";
    }

    // summary는 ai-server가 생성
    // jobId를 쓰고 싶으면 유지 가능하지만, job을 안 쓰면 transcriptId를 쓰는게 자연스러움
    public static String summaryKey(String meetingId, String transcriptId) {
        return "minutes/" + meetingId + "/" + transcriptId + "/summary.json";
    }

    // audio chunk key 규칙도 표준화할 거면 추가
    public static String audioChunkKey(String meetingId, String participantId, int chunkSeq, String ext) {
        return "audio-chunks/" + meetingId + "/" + participantId + "/" + chunkSeq + "." + ext;
    }
}
