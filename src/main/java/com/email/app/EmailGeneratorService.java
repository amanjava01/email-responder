package com.email.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com}")
    private String geminiAPIUrl;

    @Value("${gemini.api.key}")
    private String geminiAPIKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        // 1. Build the prompt
        String prompt = buildPrompt(emailRequest);

        // 2. Craft request payload expected by Gemini API
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );
        // for base url
        String baseUrl = geminiAPIUrl.endsWith("/")
                ? geminiAPIUrl.substring(0, geminiAPIUrl.length() - 1)
                : geminiAPIUrl;

        String response;
        try {
            // 3. Construct URL with query parameter & call Gemini API
            //String fullUri = geminiAPIUrl+geminiAPIKey;
            String fullUri = baseUrl + "/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiAPIKey;

            response = webClient.post()
                    .uri(fullUri)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (WebClientResponseException e) {
            System.err.println("Gemini API Error Status: " + e.getStatusCode());
            System.err.println("Gemini API Response Body: " + e.getResponseBodyAsString());
            return "Error from Gemini API: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            System.err.println("Unexpected Error: " + e.getMessage());
            return "Internal Error: " + e.getMessage();
        }

        // 4. Extract and return generated text
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);

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