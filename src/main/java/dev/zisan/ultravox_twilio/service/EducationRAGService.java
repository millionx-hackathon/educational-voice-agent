package dev.zisan.ultravox_twilio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * RAG (Retrieval Augmented Generation) service for answering student questions.
 * Uses Spring AI ChatClient with QuestionAnswerAdvisor to search textbook
 * content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EducationRAGService {

        private final ChatClient.Builder chatClientBuilder;
        private final VectorStore vectorStore;

        private static final String SYSTEM_PROMPT = """
                        You are a helpful and patient teacher assistant for students.

                        Instructions:
                        - Answer questions based only on the provided textbook content
                        - Use simple, clear language that students can understand
                        - If you don't find the answer in the textbook content, say "I couldn't find this information in the textbook"
                        - Be encouraging and supportive
                        - Keep answers concise but complete
                        - Speak naturally as if talking to a student on the phone
                        """;

        /**
         * Answer a student's question using RAG.
         * Searches the vector store for relevant textbook content and generates a
         * response.
         *
         * @param question The student's question
         * @return The AI-generated answer based on textbook content
         */
        public String answerQuestion(String question) {
                log.info("RAG Query - Question: {}", question);

                // Build chat client with RAG advisor using builder pattern (Spring AI 1.1.2+)
                ChatClient chatClient = chatClientBuilder
                                .defaultAdvisors(
                                                QuestionAnswerAdvisor.builder(vectorStore)
                                                                .searchRequest(SearchRequest.builder()
                                                                                .topK(5)
                                                                                .similarityThreshold(0.7)
                                                                                .build())
                                                                .build())
                                .build();

                // Get response
                String response = chatClient
                                .prompt()
                                .system(SYSTEM_PROMPT)
                                .user(question)
                                .call()
                                .content();

                log.debug("Generated response: {}", response);
                return response;
        }

        /**
         * Simple query without RAG - for testing chat model connectivity.
         */
        public String simpleQuery(String question) {
                log.info("Simple Query (no RAG): {}", question);

                ChatClient chatClient = chatClientBuilder.build();

                return chatClient
                                .prompt()
                                .system("You are a helpful assistant. Answer briefly.")
                                .user(question)
                                .call()
                                .content();
        }
}
