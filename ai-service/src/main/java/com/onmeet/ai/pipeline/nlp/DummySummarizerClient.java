package com.onmeet.ai.pipeline.nlp;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// CHECK [ai-담당자]: DummySummarizerClient 프로필에서 "docker"가 제거됨.
// docker 프로필에서는 ClaudeSummarizerClient가 활성화되어야 함. 해당 빈 @Profile 설정 확인 필요.
@Profile({"local","test"})
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
