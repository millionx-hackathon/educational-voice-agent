package dev.zisan.ultravox_twilio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
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

        // Use Spring AI PDF reader to extract text
        InputStreamResource resource = new InputStreamResource(file.getInputStream());
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
        List<Document> pdfDocuments = pdfReader.read();

        log.info("Extracted {} pages from PDF", pdfDocuments.size());

        // Combine all pages and re-chunk with overlap
        StringBuilder fullText = new StringBuilder();
        for (Document doc : pdfDocuments) {
            fullText.append(doc.getText()).append(" ");
        }

        // Split into overlapping chunks
        List<String> chunks = splitIntoChunks(fullText.toString());
        log.info("Split document into {} chunks", chunks.size());

        // Create Spring AI Documents with metadata
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("document_id", documentId);
            metadata.put("chunk_index", i);
            metadata.put("filename", file.getOriginalFilename());
            metadata.put("total_chunks", chunks.size());

            Document doc = new Document(chunks.get(i), metadata);
            documents.add(doc);
        }

        // Store in PGVector
        vectorStore.add(documents);

        log.info("Successfully indexed {} chunks for document: {}", chunks.size(), documentId);
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

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // Try to break at sentence boundary
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                int lastQuestion = text.lastIndexOf('?', end);
                int lastExclamation = text.lastIndexOf('!', end);

                int breakPoint = Math.max(lastPeriod, Math.max(lastQuestion, lastExclamation));

                if (breakPoint > start && breakPoint > end - 100) {
                    end = breakPoint + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty() && chunk.length() > 50) {
                chunks.add(chunk);
            }

            start = end - CHUNK_OVERLAP;
            if (start < 0)
                start = 0;
        }

        return chunks;
    }

    /**
     * Get the count of indexed documents (for health check).
     */
    public int getDocumentCount() {
        // For MVP, we just log that documents exist
        log.info("Vector store is available");
        return -1; // PGVector doesn't expose count directly
    }
}
