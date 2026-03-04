package com.onmeet.ai.pipeline.stt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@org.springframework.context.annotation.Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "openai.api-key")
public class OpenAiSttClient implements SttClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public OpenAiSttClient(
            WebClient.Builder webClientBuilder,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model,
            @Value("${openai.api-url}") String apiUrl) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(30 * 1024 * 1024))
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API Key is missing");
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, MediaType.valueOf(mimeType));
        builder.part("model", model);
        builder.part("response_format", "text"); // or json/verbose_json

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            System.err.println("OpenAI API Error [" + response.statusCode() + "]: " + body);
                            return Mono.error(new RuntimeException("OpenAI API Error: " + body));
                        }))
                .bodyToMono(String.class)
                .block();
    }
}
