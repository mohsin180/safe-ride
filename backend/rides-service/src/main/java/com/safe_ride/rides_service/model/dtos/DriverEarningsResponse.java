package com.safe_ride.rides_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Driver earnings summary — today's take plus the lifetime totals. Computed
 * over the driver's COMPLETED rides; "today" is measured against each ride's
 * {@code completedAt} (UTC), so rides completed before that field existed are
 * excluded from the today totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverEarningsResponse {
    private double todayEarnings;
    private int todayTrips;
    private double totalEarnings;
    private int totalTrips;
    private String currency;
}
