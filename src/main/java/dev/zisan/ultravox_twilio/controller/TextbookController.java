package dev.zisan.ultravox_twilio.controller;

import dev.zisan.ultravox_twilio.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for textbook upload and management.
 */
@Slf4j
@RestController
@RequestMapping("/api/textbooks")
@RequiredArgsConstructor
public class TextbookController {

    private final DocumentProcessingService documentProcessingService;

    /**
     * Upload and index a textbook PDF.
     * The PDF will be processed, chunked, and stored in the vector database.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadTextbook(
            @RequestParam("file") MultipartFile file) {

        log.info("Received textbook upload: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "File is empty"));
        }

        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Only PDF files are supported"));
        }

        try {
            String documentId = documentProcessingService.processTextbook(file);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "documentId", documentId,
                    "filename", file.getOriginalFilename(),
                    "message", "Textbook indexed successfully"));

        } catch (Exception e) {
            log.error("Error processing textbook", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", "Failed to process textbook: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint for the textbook service.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "textbook-controller"));
    }
}
