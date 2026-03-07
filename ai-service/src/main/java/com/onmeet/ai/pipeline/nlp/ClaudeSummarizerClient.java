package com.onmeet.ai.pipeline.nlp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@org.springframework.context.annotation.Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "anthropic.api-key")
public class ClaudeSummarizerClient implements SummarizerClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String apiVersion;
    private final String defaultModel;
    private final String apiUrl;
    private final ObjectMapper om;

    public ClaudeSummarizerClient(
            WebClient.Builder webClientBuilder,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.version}") String apiVersion,
            @Value("${anthropic.model}") String defaultModel,
            @Value("${anthropic.api-url}") String apiUrl,
            ObjectMapper om) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
        this.defaultModel = defaultModel;
        this.apiUrl = apiUrl;
        this.om = om;
    }

    @Override
    public String summarize(String transcriptPlainText, String language, String style, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API Key is missing");
        }

        String targetModel = (model != null && !model.isBlank()) ? model : defaultModel;
        String prompt = buildPrompt(transcriptPlainText, language, style);

        ClaudeRequest request = new ClaudeRequest(
                targetModel,
                1024,
                List.of(new Message("user", prompt)));

        try {
            String responseBody = webClient.post()
                    .uri(apiUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", apiVersion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(om.writeValueAsString(request))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractContent(responseBody);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    private String buildPrompt(String transcript, String language, String style) {
        return String.format(
                "You are a professional meeting assistant.\n" +
                        "Summarize the following meeting transcript in %s.\n" +
                        "Style: %s.\n" +
                        "Output MUST be valid JSON format with a single key 'summary'.\n" +
                        "Transcript:\n%s",
                language, style, transcript);
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = om.readTree(responseBody);
            JsonNode contentNode = root.path("content");
            if (contentNode.isArray() && contentNode.size() > 0) {
                String text = contentNode.get(0).path("text").asText();
                // Claude might wrap JSON in markdown blocks (```json ... ```)
                // Simple cleanup
                if (text.contains("```json")) {
                    text = text.replace("```json", "").replace("```", "");
                } else if (text.contains("```")) {
                    text = text.replace("```", "");
                }
                return text.trim();
            }
            return "{}";
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Claude response", e);
        }
    }

    // Inner DTOs for Claude API
    static class ClaudeRequest {
        @JsonProperty("model")
        String model;
        @JsonProperty("max_tokens")
        int maxTokens;
        @JsonProperty("messages")
        List<Message> messages;

        public ClaudeRequest(String model, int maxTokens, List<Message> messages) {
            this.model = model;
            this.maxTokens = maxTokens;
            this.messages = messages;
        }
    }

    static class Message {
        @JsonProperty("role")
        String role;
        @JsonProperty("content")
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
