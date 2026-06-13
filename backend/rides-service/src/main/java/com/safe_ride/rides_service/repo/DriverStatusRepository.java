package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverStatusRepository extends JpaRepository<DriverStatus, UUID> {
}
