package com.saferide.profile_service.repos;

import com.saferide.profile_service.models.entities.PassengerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PassengerProfileRepository extends JpaRepository<PassengerProfile, UUID> {
    boolean existsByUserId(UUID userId);

    PassengerProfile findByUserId(UUID uuid);

    /** Batch lookup used by the internal name-resolution endpoint. */
    List<PassengerProfile> findByUserIdIn(Collection<UUID> userIds);
}
