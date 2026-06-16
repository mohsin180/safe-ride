package com.safe_ride.rides_service.client;

/**
 * The road-route summary returned by {@link RoutingClient}: the driving
 * distance and duration of a trip. Distance is in kilometres, duration in
 * whole minutes.
 */
public record RouteResult(double distanceKm, int durationMin) {
}
