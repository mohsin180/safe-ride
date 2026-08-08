package com.saferide.monolith.rides.service;

import com.saferide.monolith.rides.event.NotificationPublisher;
import com.saferide.monolith.rides.event.RideNotificationEvent;
import com.saferide.monolith.rides.model.entity.Ride;
import com.saferide.monolith.rides.model.entity.RideStatus;
import com.saferide.monolith.rides.repo.RideParticipantsRepository;
import com.saferide.monolith.rides.repo.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cancels scheduled rides whose departure came and went without a driver.
 *
 * <p>Nothing used to retire them: a ride booked for last Tuesday stayed
 * PENDING forever, kept surfacing in both feeds, and — because the
 * one-ride-at-a-time guard counts PENDING — locked its host out of booking
 * again until they noticed and cancelled by hand.
 *
 * <p>On-demand rides (no departure time) are left alone; they have no moment
 * to be late for, and a host cancelling those is a deliberate act.
 */
@Component
public class StaleRideSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleRideSweeper.class);

    /** Grace after departure before a ride is considered abandoned. */
    private static final Duration GRACE = Duration.ofHours(2);

    private final RideRepository rideRepository;
    private final RideParticipantsRepository rideParticipantsRepository;
    private final NotificationPublisher notificationPublisher;

    public StaleRideSweeper(RideRepository rideRepository,
                            RideParticipantsRepository rideParticipantsRepository,
                            NotificationPublisher notificationPublisher) {
        this.rideRepository = rideRepository;
        this.rideParticipantsRepository = rideParticipantsRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Scheduled(fixedDelayString = "${rides.stale-sweep-interval-ms:900000}")
    @Transactional
    public void cancelExpiredScheduledRides() {
        Instant cutoff = Instant.now().minus(GRACE);
        List<Ride> stale = rideRepository.findExpiredScheduledRides(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Ride ride : stale) {
            ride.setStatus(RideStatus.CANCELLED);
            ride.setCancelledAt(now);
        }
        rideRepository.saveAll(stale);

        // Tell the people who were counting on it. Without this the ride
        // simply stopped appearing and joined co-passengers were never told
        // their trip was off.
        for (Ride ride : stale) {
            List<UUID> recipients = new ArrayList<>();
            recipients.add(ride.getCreatedByUserId());
            recipients.addAll(rideParticipantsRepository.findUserIdsByRideId(ride.getId()));
            notificationPublisher.publish(
                    RideNotificationEvent.RIDE_CANCELLED, ride, recipients);
        }
        log.info("Cancelled {} scheduled ride(s) whose departure passed over {} ago",
                stale.size(), GRACE);
    }
}
