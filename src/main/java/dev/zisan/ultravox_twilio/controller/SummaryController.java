package dev.zisan.ultravox_twilio.controller;

import dev.zisan.ultravox_twilio.entity.ConversationSummary;
import dev.zisan.ultravox_twilio.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing conversation summaries.
 */
@Slf4j
@RestController
@RequestMapping("/api/summaries")
@RequiredArgsConstructor
public class SummaryController {

    private final ConversationSummaryService summaryService;

    /**
     * Get all conversation summaries.
     */
    @GetMapping
    public ResponseEntity<List<ConversationSummary>> getAllSummaries() {
        log.info("Fetching all conversation summaries");
        List<ConversationSummary> summaries = summaryService.getAllSummaries();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get a specific summary by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConversationSummary> getSummaryById(@PathVariable Long id) {
        log.info("Fetching summary with ID: {}", id);
        ConversationSummary summary = summaryService.getSummaryById(id);

        if (summary == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(summary);
    }

    /**
     * Get summaries by caller number.
     */
    @GetMapping("/caller/{callerNumber}")
    public ResponseEntity<List<ConversationSummary>> getSummariesByCallerNumber(
            @PathVariable String callerNumber) {
        log.info("Fetching summaries for caller: {}", callerNumber);
        List<ConversationSummary> summaries = summaryService.getSummariesByCallerNumber(callerNumber);
        return ResponseEntity.ok(summaries);
    }

    /**
     * Manually trigger summary generation for a call.
     * Useful for testing or reprocessing failed summaries.
     * 
     * Example: POST /api/summaries/generate
     * Body: {"callId": "twilio-call-sid", "ultravoxCallId": "uuid-from-ultravox"}
     */
    @PostMapping("/generate")
    public ResponseEntity<ConversationSummary> generateSummary(
            @RequestBody Map<String, String> request) {

        String callId = request.get("callId");
        String ultravoxCallId = request.get("ultravoxCallId");
        String callerNumber = request.getOrDefault("callerNumber", "unknown");

        if (ultravoxCallId == null || ultravoxCallId.isBlank()) {
            log.error("ultravoxCallId is required for summary generation");
            return ResponseEntity.badRequest().build();
        }

        // Use a default callId if not provided
        if (callId == null || callId.isBlank()) {
            callId = "manual-" + System.currentTimeMillis();
        }

        log.info("Manually generating summary for callId: {}, ultravoxCallId: {}", callId, ultravoxCallId);
        ConversationSummary summary = summaryService.processCompletedCall(callId, ultravoxCallId, callerNumber);
        return ResponseEntity.ok(summary);
    }

    /**
     * Generate summary directly from Ultravox call ID.
     * Simpler endpoint for quick testing.
     * 
     * Example: POST /api/summaries/generate/{ultravoxCallId}
     */
    @PostMapping("/generate/{ultravoxCallId}")
    public ResponseEntity<ConversationSummary> generateSummaryByUltravoxId(
            @PathVariable String ultravoxCallId) {

        log.info("Generating summary for Ultravox call: {}", ultravoxCallId);

        String callId = "manual-" + System.currentTimeMillis();
        ConversationSummary summary = summaryService.processCompletedCall(callId, ultravoxCallId, "unknown");
        return ResponseEntity.ok(summary);
    }
}
