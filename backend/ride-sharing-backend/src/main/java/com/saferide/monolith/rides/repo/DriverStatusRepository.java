package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverStatusRepository extends JpaRepository<DriverStatus, UUID> {
}
