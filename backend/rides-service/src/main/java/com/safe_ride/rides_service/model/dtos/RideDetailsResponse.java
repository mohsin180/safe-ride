package com.safe_ride.rides_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private HostDto host;
    private Integer seatsTotal;
    private Integer seatsAvailable;
    private List<CoPassengerDto> coPassengers;
    private FareDto fare;
    private Boolean youHaveJoined;

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
}
