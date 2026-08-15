package com.saferide.monolith.rides.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableRideResponse {
    private String id;
    private String hostName;
    private Double hostRating;
    private Integer hostRatingCount;
    private Integer hostTrips;
    private String hostGender;
    /** How far the viewer is from this ride's pickup (km) + ETA to reach it,
     *  from the caller's location. Null if no location was supplied. */
    private Integer etaMinutes;
    private Double distanceKm;
    /** The ride's own trip distance (km) + driving duration (min), from the
     *  route stored at creation — so the card can show trip length up front. */
    private Double tripDistanceKm;
    private Integer tripDurationMin;
    /**
     * What THIS viewer pays: their own share if they're already on the ride,
     * otherwise what they'd pay to join. The passenger feed, the host's own
     * "Your Rides" card and the ride-details screen all show this same number,
     * so a fare never changes just because the user moved between screens.
     */
    private Double fareForRider;
    /**
     * The whole trip's fare — every rider's share added up, which is exactly
     * what the driver collects. This is the driver's number; it was previously
     * shown a per-rider estimate labelled as the trip total, so drivers saw
     * roughly half of what they'd actually earn.
     */
    private Double tripFare;
    private String pickup;
    private String drop;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Integer seatsAvailable;
    /** Co-passengers who have actually joined (accepted) this ride — what the
     *  "Riders" stat shows. Distinct from seatsAvailable (remaining capacity). */
    private Integer ridersJoined;
    /** On the "Your Rides" tab: true if you host this ride, false if you joined it. */
    private Boolean youAreHost;
}