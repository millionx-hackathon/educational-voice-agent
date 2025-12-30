package dev.zisan.ultravox_twilio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing textbook PDFs and storing them in the vector database.
 * For MVP: Supports single textbook, chunks with overlap for better context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final VectorStore vectorStore;

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    /**
     * Process and index a textbook PDF.
     * Extracts text, splits into chunks, and stores in PGVector.
     *
     * @param file The PDF file to process
     * @return The document ID assigned to this textbook
     */
    public String processTextbook(MultipartFile file) throws IOException {
        log.info("Processing textbook: {}", file.getOriginalFilename());

        String documentId = UUID.randomUUID().toString();

        // Use Apache Tika for more robust PDF extraction (handles complex PDFs better)
        InputStreamResource resource = new InputStreamResource(file.getInputStream());
        TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
        List<Document> tikaDocuments = tikaReader.read();

        log.info("Extracted {} document(s) from PDF", tikaDocuments.size());

        // Combine all content
        StringBuilder fullText = new StringBuilder();
        for (Document doc : tikaDocuments) {
            String text = doc.getText();
            if (text != null && !text.isBlank()) {
                fullText.append(text).append(" ");
            }
        }

        String content = fullText.toString();
        log.info("Total extracted text length: {} characters", content.length());

        if (content.isBlank()) {
            throw new IOException("No text could be extracted from the PDF");
        }

        // Split into overlapping chunks
        List<String> chunks = splitIntoChunks(content);
        log.info("Split document into {} chunks", chunks.size());

        // Create Spring AI Documents with metadata (process in batches to avoid OOM)
        List<Document> documents = new ArrayList<>();
        int batchSize = 50;
        int totalChunks = chunks.size();

        for (int i = 0; i < totalChunks; i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", documentId);
            metadata.put("chunk_index", i);
            metadata.put("filename", file.getOriginalFilename());
            metadata.put("total_chunks", totalChunks);

            Document doc = new Document(chunks.get(i), metadata);
            documents.add(doc);

            // Store in batches to avoid memory issues
            if (documents.size() >= batchSize || i == totalChunks - 1) {
                log.info("Storing batch of {} documents (chunk {} to {})",
                        documents.size(), i - documents.size() + 1, i);
                vectorStore.add(documents);
                documents.clear();
            }
        }

        log.info("Successfully indexed {} chunks for document: {}", totalChunks, documentId);
        return documentId;
    }

    /**
     * Split text into overlapping chunks for better context preservation.
     * Uses sentence boundaries when possible.
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        // Clean and normalize text
        text = text.replaceAll("\\s+", " ").trim();

        if (text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int start = 0;

        while (start < textLength) {
            // Calculate end position
            int end = Math.min(start + CHUNK_SIZE, textLength);

            // Try to break at sentence boundary (only if not at the very end)
            if (end < textLength) {
                // Look for sentence boundary in the last 100 chars of the chunk
                int searchStart = Math.max(start, end - 100);
                int lastPeriod = text.lastIndexOf('.', end);
                int lastQuestion = text.lastIndexOf('?', end);
                int lastExclamation = text.lastIndexOf('!', end);

                int breakPoint = Math.max(lastPeriod, Math.max(lastQuestion, lastExclamation));

                // Only use break point if it's within the search window
                if (breakPoint > searchStart) {
                    end = breakPoint + 1;
                }
            }

            // Extract the chunk
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty() && chunk.length() > 50) {
                chunks.add(chunk);
            }

            // Move to next position with overlap
            // IMPORTANT: Always advance start to prevent infinite loop
            int nextStart = end - CHUNK_OVERLAP;
            if (nextStart <= start) {
                // If we're not making progress, just move to end
                start = end;
            } else {
                start = nextStart;
            }

            // Safety check: if we've processed enough chunks, break
            if (chunks.size() > 10000) {
                log.warn("Reached maximum chunk limit of 10000, stopping");
                break;
            }
        }

        return chunks;
    }

    /**
     * Get the count of indexed documents (for health check).
     */
    public int getDocumentCount() {
        log.info("Vector store is available");
        return -1; // PGVector doesn't expose count directly
    }
}
