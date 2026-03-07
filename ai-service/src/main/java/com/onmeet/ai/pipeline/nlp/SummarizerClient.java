package com.onmeet.ai.pipeline.nlp;

public interface SummarizerClient {
    /**
     * @return summary json string
     */
    String summarize(String transcriptPlainText, String language, String style, String model);
}
