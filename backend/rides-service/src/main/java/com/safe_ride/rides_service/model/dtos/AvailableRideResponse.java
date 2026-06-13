package com.safe_ride.rides_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Integer etaMinutes;
    private Double distanceKm;
    private Double fareForRider;
    private String pickup;
    private String drop;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Integer seatsAvailable;
    /** On the "Your Rides" tab: true if you host this ride, false if you joined it. */
    private Boolean youAreHost;
}