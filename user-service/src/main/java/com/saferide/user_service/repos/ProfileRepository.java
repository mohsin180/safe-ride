package com.saferide.user_service.repos;

import com.saferide.user_service.model.entities.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Boolean existsByUserId(UUID userId);

    Optional<Profile> findByUserId(UUID userId);
}
