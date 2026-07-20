package com.email.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiAPIUrl;

    @Value("${gemini.api.key}")
    private String geminiAPIKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        // 1. Build the prompt
        String prompt = buildPrompt(emailRequest);

        // 2. Craft the request payload (Gemini expects "text", not "type")
        Map<String, Object> requestBody = Map.of(
                "contents", java.util.List.of(
                        Map.of("parts", java.util.List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        // 3. Execute request and get response string
        String response = webClient.post()
                .uri(geminiAPIUrl + geminiAPIKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody) // Added missing body payload here
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // 4. Extract and return the final text
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);

            // Fixed typo: changed "candidated" to "candidates"
            return rootNode.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }

    public String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate an email reply for the following email content. Please do not generate a subject line.\n");

        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone.\n");
        }

        prompt.append("\nOriginal Email:\n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}