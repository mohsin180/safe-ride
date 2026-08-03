package com.saferide.monolith.rides.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One row in the driver ride-history screen — a ride the driver was assigned
 * to that has since COMPLETED or CANCELLED. {@code fare} is the driver's gross
 * for the trip (the un-split total). {@code passengerName} is the host who
 * created the ride. {@code completedAt} is the completion time for completed
 * rides (cancelled rides carry the cancellation time), falling back to the
 * ride's creation time when neither is set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverRideHistoryResponse {
    private String id;
    private String pickup;
    private String drop;
    private String status;
    private OffsetDateTime completedAt;
    private Double fare;
    private String passengerName;
}
