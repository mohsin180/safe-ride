package com.saferide.messaging_service.repo;

import com.saferide.messaging_service.model.entity.ChatReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatReadStateRepository extends JpaRepository<ChatReadState, UUID> {

    Optional<ChatReadState> findByUserIdAndRideId(UUID userId, UUID rideId);
}
