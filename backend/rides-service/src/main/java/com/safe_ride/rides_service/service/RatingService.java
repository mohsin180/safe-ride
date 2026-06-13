package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.client.DriverClient;
import com.safe_ride.rides_service.client.ProfileClient;
import com.safe_ride.rides_service.client.RatingUpdateRequest;
import com.safe_ride.rides_service.config.UserContext;
import com.safe_ride.rides_service.event.NotificationPublisher;
import com.safe_ride.rides_service.event.RideNotificationEvent;
import com.safe_ride.rides_service.exceptions.ConflictException;
import com.safe_ride.rides_service.exceptions.ForbiddenException;
import com.safe_ride.rides_service.exceptions.NotFoundException;
import com.safe_ride.rides_service.model.entity.RatedRole;
import com.safe_ride.rides_service.model.entity.Rating;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideStatus;
import com.safe_ride.rides_service.repo.RatingRepository;
import com.safe_ride.rides_service.repo.RideDepartureRepository;
import com.safe_ride.rides_service.repo.RideParticipantsRepository;
import com.safe_ride.rides_service.repo.RideRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Bidirectional post-trip ratings on COMPLETED rides: passengers rate the
 * driver, the driver rates each passenger. rides-service owns the
 * individual {@link Rating} rows; after each submission it recomputes the
 * rated user's average and pushes that aggregate to profile-service so all
 * the existing summary reads (host/driver cards, stats) light up.
 */
@Service
public class RatingService {

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final RideDepartureRepository rideDepartureRepository;
    private final RatingRepository ratingRepository;
    private final ProfileClient profileClient;
    private final DriverClient driverClient;
    private final NotificationPublisher notificationPublisher;

    public RatingService(RideRepository rideRepository,
                         RideParticipantsRepository rideParticipantsRepository,
                         RideDepartureRepository rideDepartureRepository,
                         RatingRepository ratingRepository,
                         ProfileClient profileClient,
                         DriverClient driverClient,
                         NotificationPublisher notificationPublisher) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.rideDepartureRepository = rideDepartureRepository;
        this.ratingRepository = ratingRepository;
        this.profileClient = profileClient;
        this.driverClient = driverClient;
        this.notificationPublisher = notificationPublisher;
    }

    /** A passenger (host, co-passenger, or one who already left) rates the driver. */
    @Transactional
    public void rateDriver(UUID rideId, int stars) {
        UUID raterId = currentUserId();
        Ride ride = ratableRide(rideId);

        if (!wasPassengerMember(ride, raterId)) {
            throw new ForbiddenException("Only riders on this trip can rate the driver");
        }
        UUID driverId = ride.getDriverId();
        if (driverId == null) {
            throw new ConflictException("This ride had no driver to rate");
        }
        if (driverId.equals(raterId)) {
            throw new ForbiddenException("You cannot rate yourself");
        }

        saveRating(rideId, raterId, driverId, RatedRole.DRIVER, stars);
        recomputeAndPush(driverId, RatedRole.DRIVER);
        notificationPublisher.publish(
                RideNotificationEvent.RATING_RECEIVED, ride, java.util.List.of(driverId), stars);
    }

    /**
     * The driver rates one passenger (host, co-passenger, or one who already
     * left mid-trip) of the ride. Works on an active or completed trip.
     */
    @Transactional
    public void ratePassenger(UUID rideId, UUID ratedId, int stars) {
        UUID raterId = currentUserId();
        Ride ride = ratableRide(rideId);

        if (ride.getDriverId() == null || !ride.getDriverId().equals(raterId)) {
            throw new ForbiddenException("Only the trip's driver can rate passengers");
        }
        if (ratedId.equals(raterId)) {
            throw new ForbiddenException("You cannot rate yourself");
        }
        if (!wasPassengerMember(ride, ratedId)) {
            throw new ForbiddenException("That person wasn't a passenger on this trip");
        }

        saveRating(rideId, raterId, ratedId, RatedRole.PASSENGER, stars);
        recomputeAndPush(ratedId, RatedRole.PASSENGER);
        notificationPublisher.publish(
                RideNotificationEvent.RATING_RECEIVED, ride, java.util.List.of(ratedId), stars);
    }

    /**
     * A passenger rates a co-passenger. Used by the bidirectional leave-time
     * flow: the passenger leaving rates the others, and remaining members
     * rate the one who left. Works while the trip is in progress (not only
     * after completion), and recognizes members who have already left.
     */
    @Transactional
    public void rateCoPassenger(UUID rideId, UUID ratedId, int stars) {
        UUID raterId = currentUserId();
        Ride ride = ratableRide(rideId);

        if (!wasPassengerMember(ride, raterId)) {
            throw new ForbiddenException("Only riders on this trip can rate co-passengers");
        }
        if (ratedId.equals(raterId)) {
            throw new ForbiddenException("You cannot rate yourself");
        }
        if (ratedId.equals(ride.getDriverId())) {
            throw new ForbiddenException("Rate the driver with a driver rating");
        }
        if (!wasPassengerMember(ride, ratedId)) {
            throw new ForbiddenException("That person wasn't a passenger on this trip");
        }

        saveRating(rideId, raterId, ratedId, RatedRole.PASSENGER, stars);
        recomputeAndPush(ratedId, RatedRole.PASSENGER);
        notificationPublisher.publish(
                RideNotificationEvent.RATING_RECEIVED, ride, java.util.List.of(ratedId), stars);
    }

    // ── internals ──────────────────────────────────────────

    /** A ride that's worth rating — an active or finished trip, not pending/cancelled. */
    private Ride ratableRide(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
        RideStatus status = ride.getStatus();
        if (status != RideStatus.ACCEPTED
                && status != RideStatus.STARTED
                && status != RideStatus.COMPLETED) {
            throw new ConflictException("This ride can't be rated");
        }
        return ride;
    }

    private boolean isPassengerOfRide(Ride ride, UUID userId) {
        return userId.equals(ride.getCreatedByUserId())
                || rideParticipantsRepository.existsByRide_IdAndUserId(ride.getId(), userId);
    }

    /** Host, a current co-passenger, OR someone who already left this ride. */
    private boolean wasPassengerMember(Ride ride, UUID userId) {
        return isPassengerOfRide(ride, userId)
                || rideDepartureRepository.existsByRideIdAndUserId(ride.getId(), userId);
    }

    private void saveRating(UUID rideId, UUID raterId, UUID ratedId,
                            RatedRole role, int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("stars must be between 1 and 5");
        }
        if (ratingRepository.existsByRideIdAndRaterIdAndRatedId(rideId, raterId, ratedId)) {
            throw new ConflictException("You've already rated this trip");
        }
        Rating rating = new Rating();
        rating.setRideId(rideId);
        rating.setRaterId(raterId);
        rating.setRatedId(ratedId);
        rating.setRatedRole(role);
        rating.setStars(stars);
        ratingRepository.save(rating);
    }

    /**
     * Recompute the rated user's average and cache it on their profile.
     * Best-effort: the rating is already persisted here, so if
     * profile-service is briefly unreachable the aggregate just refreshes
     * on the next rating rather than failing the request.
     */
    private void recomputeAndPush(UUID ratedId, RatedRole role) {
        Double average = round1(ratingRepository.averageForRatedId(ratedId));
        try {
            RatingUpdateRequest body = new RatingUpdateRequest(average);
            if (role == RatedRole.DRIVER) {
                driverClient.updateDriverRating(ratedId, body);
            } else {
                profileClient.updatePassengerRating(ratedId, body);
            }
        } catch (Exception ignored) {
            // Aggregate cache update is non-critical; the rating is saved.
        }
    }

    private static Double round1(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return ((UserContext) authentication.getDetails()).userId();
    }
}
