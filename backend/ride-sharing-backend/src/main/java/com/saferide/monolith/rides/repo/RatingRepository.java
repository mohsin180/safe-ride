package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.RatedRole;
import com.saferide.monolith.rides.model.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByRideIdAndRaterIdAndRatedId(UUID rideId, UUID raterId, UUID ratedId);

    /** Average stars a user has received across all rides — the value we
     *  cache onto their profile. Null when they've never been rated. */
    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.ratedId = :ratedId")
    Double averageForRatedId(@Param("ratedId") UUID ratedId);

    /** How many ratings a user has received — shown next to the average so
     *  a single 5-star rating doesn't read as a flat "5.0". */
    long countByRatedId(UUID ratedId);

    /** The ratings a given rater left across a set of rides — used to fill
     *  in each history row's "rating you gave" without an N+1 lookup. */
    List<Rating> findByRaterIdAndRideIdInAndRatedRole(
            UUID raterId, Collection<UUID> rideIds, RatedRole ratedRole);
}
