package dev.zisan.ultravox_twilio.controller;

import dev.zisan.ultravox_twilio.service.EducationRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for testing RAG queries directly.
 * Useful for development and debugging.
 */
@Slf4j
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final EducationRAGService ragService;

    /**
     * Test endpoint to query the knowledge base directly.
     * Use this to verify RAG is working before testing with voice.
     */
    @PostMapping("/ask")
    public Map<String, String> askQuestion(@RequestBody Map<String, String> request) {

        String question = request.get("question");

        if (question == null || question.isBlank()) {
            return Map.of(
                    "error", "Question is required");
        }

        log.info("Test query received: {}", question);

        try {
            String answer = ragService.answerQuestion(question);

            return Map.of(
                    "question", question,
                    "answer", answer,
                    "source", "textbook-rag");
        } catch (Exception e) {
            log.error("Error answering question", e);
            return Map.of(
                    "question", question,
                    "error", e.getMessage());
        }
    }

    /**
     * Simple test endpoint without RAG - tests chat model connectivity.
     */
    @PostMapping("/simple")
    public Map<String, String> simpleQuery(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "Hello");

        log.info("Simple query (no RAG): {}", question);

        try {
            String answer = ragService.simpleQuery(question);
            return Map.of(
                    "question", question,
                    "answer", answer);
        } catch (Exception e) {
            log.error("Error in simple query", e);
            return Map.of(
                    "question", question,
                    "error", e.getMessage());
        }
    }
}
