package com.saferide.monolith.profile.repos;

import com.saferide.monolith.profile.models.entities.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    boolean existsByUserId(UUID userId);

    DriverProfile findByUserId(UUID userId);

    /**
     * Batch lookup for the internal driver-summary endpoint. JOIN FETCH the
     * vehicle so {@code carInfo} can be composed outside the transaction
     * without tripping a LazyInitializationException (vehicle is LAZY).
     */
    @Query("SELECT d FROM DriverProfile d LEFT JOIN FETCH d.vehicle WHERE d.userId IN :ids")
    List<DriverProfile> findByUserIdInFetchVehicle(@Param("ids") Collection<UUID> ids);
}
