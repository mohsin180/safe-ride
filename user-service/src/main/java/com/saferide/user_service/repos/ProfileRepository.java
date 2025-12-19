package com.saferide.user_service.repos;

import com.saferide.user_service.model.entities.PassengerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<PassengerProfile, UUID> {
    Boolean existsByUserId(UUID userId);

    Optional<PassengerProfile> findByUserId(UUID userId);
}
