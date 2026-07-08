package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Misconduct reports queue. */
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
}
