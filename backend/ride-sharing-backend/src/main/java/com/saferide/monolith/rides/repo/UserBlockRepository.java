package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Who has blocked whom — used to keep blocked users out of each other's feed. */
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** Users this user has blocked. */
    @Query("SELECT b.blockedId FROM UserBlock b WHERE b.blockerId = :userId")
    List<UUID> findBlockedBy(@Param("userId") UUID userId);

    /** Users who have blocked this user. */
    @Query("SELECT b.blockerId FROM UserBlock b WHERE b.blockedId = :userId")
    List<UUID> findBlockersOf(@Param("userId") UUID userId);
}
