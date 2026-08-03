package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Misconduct reports queue. */
public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
}
