package com.safe_ride.rides_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideDetailsResponse {
    private String id;
    private String pickup;
    private String drop;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private String rideType;
    private String status;
    private OffsetDateTime createdAt;
    /** Scheduled departure; null = on-demand ("leave now"). */
    private Instant departureTime;
    /** The VIEWER's own booked seats — the host's party for the host, a
     *  co-passenger's chosen seats for them. Null for a non-member browsing. */
    private Integer yourSeats;

    private HostDto host;
    private Integer seatsTotal;
    private Integer seatsAvailable;
    private List<CoPassengerDto> coPassengers;
    /** Every stop on the shared route — the host's pickup/drop plus each
     *  joined co-passenger's pickup/drop — so the map can draw the full
     *  multi-stop polyline. Unordered; the client orders by shortest path. */
    private List<StopDto> stops;
    private FareDto fare;
    private Boolean youHaveJoined;
    /** Whether the host has published this ride to the driver feed yet.
     *  Drives the host's "Publish to drivers" vs "Waiting for a driver" CTA. */
    private Boolean publishedToDrivers;
    /** True when the viewer has a still-PENDING join request for this ride
     *  (requested but not yet accepted/declined by the host). Lets the
     *  client show a persistent "Request sent" state instead of offering
     *  the join button again. Always false for the host and for anyone
     *  already joined. */
    private Boolean youHaveRequested;
    /** The viewer's OWN requested route for this ride (from their join
     *  request), so the client can show "Your Route" as the co-passenger's
     *  own pickup/drop instead of the host's. Null for the host and for
     *  viewers with no active (pending/accepted) request. */
    private String yourPickup;
    private String yourDrop;
    /** The assigned driver, once one accepts the ride; null while PENDING
     *  or if the driver profile can't be resolved. Powers the passenger
     *  active-trip driver card. */
    private DriverDto driver;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostDto {
        private String id;
        private String name;
        private Double rating;
        private Integer ratingCount;
        private Integer trips;
        private String gender;
        /** Contact number, so the assigned driver can call to coordinate. */
        private String phone;
        /** Trip progress once STARTED: WAITING | PICKED | DROPPED. Null before start. */
        private String pickupStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoPassengerDto {
        private String id;
        private String name;
        private Double rating;
        private Integer ratingCount;
        private Integer trips;
        private String gender;
        /** Contact number, so the assigned driver can call to coordinate. */
        private String phone;
        /** Trip progress once STARTED: WAITING | PICKED | DROPPED. Null before start. */
        private String pickupStatus;
    }

    /** One stop on the shared route. [ownerId] pairs a person's PICKUP with
     *  their DROP so the client can keep pickup-before-drop when ordering. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopDto {
        private String ownerId;
        private String label;   // person's name (or host's) — shown on the marker
        private String kind;    // "PICKUP" | "DROP"
        private Double lat;
        private Double lng;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FareDto {
        private Double baseFare;
        private Double sharedDiscount;
        private Double perRider;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverDto {
        private String id;
        private String name;
        private String carInfo;
        private Double rating;
        private String phone;
    }
}
