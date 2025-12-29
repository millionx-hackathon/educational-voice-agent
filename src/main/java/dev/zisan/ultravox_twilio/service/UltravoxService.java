package dev.zisan.ultravox_twilio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Service for interacting with Ultravox Voice AI API.
 * Creates voice AI calls and configures RAG tools for textbook queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UltravoxService {

    @Value("${ultravox.api-key}")
    private String apiKey;

    @Value("${ultravox.api-url}")
    private String apiUrl;

    @Value("${ultravox.model}")
    private String model;

    @Value("${ultravox.voice}")
    private String voice;

    @Value("${ultravox.temperature}")
    private double temperature;

    @Value("${server.port:8080}")
    private int serverPort;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private static final String SYSTEM_PROMPT = """
            You are a helpful and patient teacher assistant for students.
            You are helping students learn from their textbook over the phone.

            Instructions:
            - Greet the student warmly when the call starts
            - Ask what topic they would like to learn about
            - Use the searchTextbook tool to find relevant information
            - Speak slowly and clearly, suitable for phone conversation
            - Use simple language that students can easily understand
            - Be very patient and encouraging
            - If the student seems confused, explain in simpler terms
            - Keep responses concise - under 30 seconds of speech
            - If you cannot find information in the textbook, say so politely
            """;

    /**
     * Create an Ultravox call with Twilio medium and RAG tool.
     *
     * @param callSid The Twilio call SID for reference
     * @param baseUrl The base URL for callback (ngrok URL in development)
     * @return The joinUrl for Twilio to connect to
     */
    public String createCall(String callSid, String baseUrl) throws IOException {
        log.info("Creating Ultravox call for CallSid: {}", callSid);

        Map<String, Object> callConfig = new HashMap<>();
        callConfig.put("systemPrompt", SYSTEM_PROMPT);
        callConfig.put("model", model);
        callConfig.put("voice", voice);
        callConfig.put("temperature", temperature);
        callConfig.put("firstSpeaker", "FIRST_SPEAKER_AGENT");

        // Configure for Twilio WebSocket connection
        Map<String, Object> medium = new HashMap<>();
        medium.put("twilio", new HashMap<>());
        callConfig.put("medium", medium);

        // Add RAG tool configuration
        callConfig.put("selectedTools", buildRagTools(baseUrl));

        // Make API request to create call
        String json = objectMapper.writeValueAsString(callConfig);
        log.debug("Ultravox request body: {}", json);

        RequestBody body = RequestBody.create(
                json,
                okhttp3.MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(apiUrl + "/calls")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-API-Key", apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Failed to create Ultravox call: {} - {}", response.code(), responseBody);
                throw new IOException("Failed to create Ultravox call: " + response.code());
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String joinUrl = responseJson.get("joinUrl").asText();

            log.info("Ultravox call created. Join URL: {}", joinUrl);
            return joinUrl;
        }
    }

    /**
     * Build RAG tool configuration for Ultravox.
     * This tool allows Ultravox to call our RAG endpoint during conversation.
     */
    private List<Map<String, Object>> buildRagTools(String baseUrl) {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> ragTool = new HashMap<>();
        Map<String, Object> temporaryTool = new HashMap<>();

        temporaryTool.put("modelToolName", "searchTextbook");
        temporaryTool.put("description",
                "Searches the textbook to find relevant information to answer the student's question. " +
                        "Use this tool when the student asks about any topic from their textbook or course material.");

        // Define parameters
        List<Map<String, Object>> parameters = new ArrayList<>();

        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("name", "question");
        queryParam.put("location", "PARAMETER_LOCATION_BODY");
        queryParam.put("required", true);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "string");
        schema.put("description", "The student's question to search for in the textbook");
        queryParam.put("schema", schema);
        parameters.add(queryParam);

        temporaryTool.put("dynamicParameters", parameters);

        // HTTP configuration - points to our RAG endpoint
        Map<String, Object> http = new HashMap<>();
        http.put("baseUrlPattern", baseUrl + "/api/rag/query");
        http.put("httpMethod", "POST");
        temporaryTool.put("http", http);

        ragTool.put("temporaryTool", temporaryTool);
        tools.add(ragTool);

        log.debug("Configured RAG tool with endpoint: {}/api/rag/query", baseUrl);
        return tools;
    }
}
