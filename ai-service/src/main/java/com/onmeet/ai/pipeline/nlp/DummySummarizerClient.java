package com.onmeet.ai.pipeline.nlp;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"local","docker","test"})
@Component
public class DummySummarizerClient implements SummarizerClient {

    @Override
    public String summarize(String transcriptPlainText, String language, String style, String model) {
        // ✅ Claude 붙이기 전까지 파이프라인 테스트용
        String safe = transcriptPlainText.length() > 120 ? transcriptPlainText.substring(0, 120) : transcriptPlainText;
        return """
               {
                 "model":"%s",
                 "language":"%s",
                 "style":"%s",
                 "summary":"%s..."
               }
               """.formatted(model, language, style, safe.replace("\"","\\\"").replace("\n"," "));
    }
}
