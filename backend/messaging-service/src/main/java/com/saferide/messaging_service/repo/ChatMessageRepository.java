package com.saferide.messaging_service.repo;

import com.saferide.messaging_service.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByRideIdOrderBySentAtAsc(UUID rideId);

    Optional<ChatMessage> findTopByRideIdOrderBySentAtDesc(UUID rideId);

    /** Unread = messages in this ride after the cutoff, not sent by the reader. */
    long countByRideIdAndSentAtAfterAndSenderIdNot(UUID rideId, Instant after, UUID senderId);
}
