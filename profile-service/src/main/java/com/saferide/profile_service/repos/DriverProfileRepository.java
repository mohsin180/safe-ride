package com.saferide.profile_service.repos;

import com.saferide.profile_service.models.entities.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    boolean existsByUserId(UUID userId);
}
