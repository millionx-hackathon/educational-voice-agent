package dev.zisan.ultravox_twilio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing conversation summaries after each call ends.
 */
@Entity
@Table(name = "conversation_summaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false)
    private String callId;

    @Column(name = "caller_number")
    private String callerNumber;

    @Column(name = "ultravox_call_id")
    private String ultravoxCallId;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "topics_discussed", columnDefinition = "TEXT")
    private String topicsDiscussed;

    @Column(name = "call_duration_seconds")
    private Integer callDurationSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "call_started_at")
    private LocalDateTime callStartedAt;

    @Column(name = "call_ended_at")
    private LocalDateTime callEndedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
