package dev.zisan.ultravox_twilio.repository;

import dev.zisan.ultravox_twilio.entity.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for conversation summaries.
 */
@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {

    /**
     * Find summary by Twilio call ID.
     */
    Optional<ConversationSummary> findByCallId(String callId);

    /**
     * Find summary by Ultravox call ID.
     */
    Optional<ConversationSummary> findByUltravoxCallId(String ultravoxCallId);

    /**
     * Find all summaries ordered by creation date descending.
     */
    List<ConversationSummary> findAllByOrderByCreatedAtDesc();

    /**
     * Find summaries by caller number.
     */
    List<ConversationSummary> findByCallerNumberOrderByCreatedAtDesc(String callerNumber);
}
