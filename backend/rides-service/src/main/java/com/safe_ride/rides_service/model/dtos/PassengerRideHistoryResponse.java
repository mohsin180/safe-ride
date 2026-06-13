package com.safe_ride.rides_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One row in the passenger ride-history screen — a ride the user created
 * that has since COMPLETED or CANCELLED. driverName / carInfo / ratingGiven
 * stay null until the driver-side ride flow exists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRideHistoryResponse {
    private String id;
    private String pickup;
    private String drop;
    private String status;
    private OffsetDateTime completedAt;
    private Double fare;
    private String driverName;
    private String carInfo;
    private Double ratingGiven;
}
