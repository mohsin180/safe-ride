package com.saferide.monolith.messaging.repo;

import com.saferide.monolith.messaging.model.entity.ChatReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatReadStateRepository extends JpaRepository<ChatReadState, UUID> {

    Optional<ChatReadState> findByUserIdAndRideId(UUID userId, UUID rideId);
}
