package com.safe_ride.user_service.repos;

import com.safe_ride.user_service.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepo extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used =true where t.users.id =:userId AND t.used=false")
    void invalidateUnusedTokensByUserId(@Param("userId") UUID userId);
}
