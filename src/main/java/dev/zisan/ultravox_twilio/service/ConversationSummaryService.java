package dev.zisan.ultravox_twilio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zisan.ultravox_twilio.entity.ConversationSummary;
import dev.zisan.ultravox_twilio.repository.ConversationSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing conversation summaries.
 * Fetches conversation data from Ultravox and generates AI summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final ConversationSummaryRepository summaryRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Value("${ultravox.api-key}")
    private String ultravoxApiKey;

    @Value("${ultravox.api-url}")
    private String ultravoxApiUrl;

    private static final String SUMMARY_PROMPT = """
            You are an expert at creating concise educational summaries.

            Based on the following conversation transcript between a student and an AI tutor,
            create a brief summary including:
            1. Main topics discussed (comma-separated list)
            2. Key questions the student asked
            3. Overall summary (2-3 sentences)

            Format your response as:
            TOPICS: [topics]
            SUMMARY: [summary]

            Transcript:
            %s
            """;

    /**
     * Process a completed call and create a summary.
     *
     * @param callId         Twilio call SID
     * @param ultravoxCallId Ultravox call ID
     * @param callerNumber   Caller's phone number
     */
    public ConversationSummary processCompletedCall(String callId, String ultravoxCallId, String callerNumber) {
        log.info("Processing completed call: {} (Ultravox: {})", callId, ultravoxCallId);

        try {
            // Fetch transcript from Ultravox
            String transcript = fetchTranscript(ultravoxCallId);

            if (transcript == null || transcript.isBlank()) {
                log.warn("No transcript available for call: {}", ultravoxCallId);
                transcript = "No transcript available";
            }

            // Generate summary using AI
            String summaryResponse = generateSummary(transcript);

            // Parse the summary response
            String topics = extractTopics(summaryResponse);
            String summary = extractSummary(summaryResponse);

            // Create and save summary entity
            ConversationSummary conversationSummary = ConversationSummary.builder()
                    .callId(callId)
                    .ultravoxCallId(ultravoxCallId)
                    .callerNumber(callerNumber)
                    .summary(summary)
                    .topicsDiscussed(topics)
                    .callEndedAt(LocalDateTime.now())
                    .build();

            ConversationSummary saved = summaryRepository.save(conversationSummary);
            log.info("Saved conversation summary with ID: {}", saved.getId());

            return saved;

        } catch (Exception e) {
            log.error("Error processing completed call: {}", callId, e);

            // Save a minimal record even if processing fails
            ConversationSummary errorSummary = ConversationSummary.builder()
                    .callId(callId)
                    .ultravoxCallId(ultravoxCallId)
                    .callerNumber(callerNumber)
                    .summary("Error processing call: " + e.getMessage())
                    .callEndedAt(LocalDateTime.now())
                    .build();

            return summaryRepository.save(errorSummary);
        }
    }

    /**
     * Fetch transcript from Ultravox API.
     */
    private String fetchTranscript(String ultravoxCallId) throws IOException {
        String url = ultravoxApiUrl + "/calls/" + ultravoxCallId + "/messages";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-API-Key", ultravoxApiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Failed to fetch transcript: {} - {}", response.code(), response.message());
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode messagesJson = objectMapper.readTree(responseBody);

            StringBuilder transcript = new StringBuilder();

            if (messagesJson.has("results")) {
                for (JsonNode message : messagesJson.get("results")) {
                    String role = message.has("role") ? message.get("role").asText() : "unknown";
                    String text = message.has("text") ? message.get("text").asText() : "";

                    if (!text.isBlank()) {
                        transcript.append(role).append(": ").append(text).append("\n");
                    }
                }
            }

            return transcript.toString();
        }
    }

    /**
     * Generate a summary using the AI model.
     */
    private String generateSummary(String transcript) {
        ChatClient chatClient = chatClientBuilder.build();

        return chatClient
                .prompt()
                .user(String.format(SUMMARY_PROMPT, transcript))
                .call()
                .content();
    }

    /**
     * Extract topics from the AI response.
     */
    private String extractTopics(String response) {
        if (response == null)
            return "Unknown";

        int topicsStart = response.indexOf("TOPICS:");
        int summaryStart = response.indexOf("SUMMARY:");

        if (topicsStart >= 0 && summaryStart > topicsStart) {
            return response.substring(topicsStart + 7, summaryStart).trim();
        }

        return "General ICT topics";
    }

    /**
     * Extract summary from the AI response.
     */
    private String extractSummary(String response) {
        if (response == null)
            return "No summary available";

        int summaryStart = response.indexOf("SUMMARY:");

        if (summaryStart >= 0) {
            return response.substring(summaryStart + 8).trim();
        }

        return response;
    }

    /**
     * Get all conversation summaries.
     */
    public List<ConversationSummary> getAllSummaries() {
        return summaryRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get summaries for a specific caller.
     */
    public List<ConversationSummary> getSummariesByCallerNumber(String callerNumber) {
        return summaryRepository.findByCallerNumberOrderByCreatedAtDesc(callerNumber);
    }

    /**
     * Get a specific summary by ID.
     */
    public ConversationSummary getSummaryById(Long id) {
        return summaryRepository.findById(id).orElse(null);
    }
}
