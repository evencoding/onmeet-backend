package com.onmeet.ai.pipeline.storage;

public final class StorageKeyFactory {

    private StorageKeyFactory() {}

    public static String transcriptKey(Long roomId, String transcriptId) {
        return "transcripts/" + roomId + "/" + transcriptId + ".json";
    }

    public static String summaryKey(Long roomId, String transcriptId) {
        return "minutes/" + roomId + "/" + transcriptId + "/summary.json";
    }

    public static String audioChunkKey(Long roomId, Long userId, int chunkSeq, String ext) {
        return "audio-chunks/" + roomId + "/" + userId + "/" + chunkSeq + "." + ext;
    }
}
