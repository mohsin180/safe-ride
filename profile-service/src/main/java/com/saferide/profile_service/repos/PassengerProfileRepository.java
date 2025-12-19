package com.saferide.profile_service.repos;

import com.saferide.profile_service.models.entities.PassengerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PassengerProfileRepository extends JpaRepository<PassengerProfile, UUID> {
}
