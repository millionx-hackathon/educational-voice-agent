package dev.zisan.ultravox_twilio.controller;

import dev.zisan.ultravox_twilio.service.EducationRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG endpoint called by Ultravox during voice conversations.
 * This is the "tool" that Ultravox uses to search the textbook.
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final EducationRAGService ragService;

    /**
     * RAG endpoint called by Ultravox during conversation.
     * Ultravox sends the student's question here to get textbook-based answers.
     * 
     * Returns the answer in "result" field which Ultravox reads as the tool output.
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, String> request) {

        String question = request.get("question");

        if (question == null || question.isBlank()) {
            log.warn("RAG query called with empty question");
            return ResponseEntity.badRequest().body(Map.of(
                    "result", "No question was provided. Please ask a specific question."));
        }

        log.info("RAG tool called by Ultravox - Question: {}", question);

        try {
            String answer = ragService.answerQuestion(question);

            log.info("RAG response generated, length: {} chars", answer.length());
            log.debug("RAG answer content: {}", answer);

            // Ultravox expects the tool output in a "result" field
            return ResponseEntity.ok(Map.of(
                    "result", answer));

        } catch (Exception e) {
            log.error("Error in RAG query", e);
            return ResponseEntity.ok(Map.of(
                    "result",
                    "I couldn't find information about that in the textbook. Please try asking in a different way."));
        }
    }
}
