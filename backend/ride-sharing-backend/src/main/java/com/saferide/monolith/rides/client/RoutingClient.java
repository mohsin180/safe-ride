package com.saferide.monolith.rides.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side client for the Geoapify routing API. Given pickup/drop coords it
 * returns the road (driving) distance + duration of the trip, mirroring the
 * exact API the Flutter frontend's {@code RoutingService} uses:
 * <pre>
 *   GET {routing-url}?waypoints={pickupLat},{pickupLng}|{dropLat},{dropLng}
 *       &amp;mode=drive&amp;apiKey={key}
 * </pre>
 * Geoapify replies with GeoJSON; we read {@code features[0].properties.distance}
 * (METERS) and {@code features[0].properties.time} (SECONDS).
 *
 * <p>Resilient by design: if the api-key is blank, or the network call /
 * response parse fails for any reason, it returns {@link Optional#empty()}
 * rather than throwing. Pricing then falls back to the Haversine estimate, so a
 * missing {@code GEOAPIFY_KEY} never breaks ride creation.
 */
@Component
public class RoutingClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingClient.class);

    private final RestClient client;
    private final String apiKey;
    private final String routingUrl;

    public RoutingClient(
            @Value("${geoapify.api-key:}") String apiKey,
            @Value("${geoapify.routing-url:https://api.geoapify.com/v1/routing}") String routingUrl) {
        this.apiKey = apiKey;
        this.routingUrl = routingUrl;
        this.client = RestClient.create();
    }

    /**
     * Driving route between pickup and drop, or {@link Optional#empty()} when
     * the key is missing or the call/parse fails. Never throws.
     */
    public Optional<RouteResult> route(double pickupLat, double pickupLng,
                                       double dropLat, double dropLng) {
        if (apiKey == null || apiKey.isBlank()) {
            // No key configured — silently fall back to Haversine pricing.
            return Optional.empty();
        }
        try {
            // Geoapify wants waypoints as "lat,lng|lat,lng" (lat first on input).
            String waypoints = pickupLat + "," + pickupLng + "|" + dropLat + "," + dropLng;
            // Build an explicitly-encoded java.net.URI (the '|' is illegal in a
            // raw URI → %7C) and pass it as a URI so RestClient does NOT re-run
            // template processing on it.
            java.net.URI uri = UriComponentsBuilder.fromUriString(routingUrl)
                    .queryParam("waypoints", waypoints)
                    .queryParam("mode", "drive")
                    .queryParam("apiKey", apiKey)
                    .build()
                    .encode()
                    .toUri();

            Map<String, Object> body = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(GEOJSON_TYPE);

            return parse(body);
        } catch (Exception e) {
            log.warn("Geoapify routing call failed ({}, {} -> {}, {}): {}",
                    pickupLat, pickupLng, dropLat, dropLng, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Driving route through an ORDERED list of waypoints (each a
     * {@code [lat, lng]}), for the full multi-stop shared route. Returns the
     * total road distance + duration, or {@link Optional#empty()} when the key
     * is missing, there are fewer than 2 points, or the call/parse fails.
     * Never throws.
     */
    public Optional<RouteResult> routeThrough(List<double[]> points) {
        if (apiKey == null || apiKey.isBlank() || points == null || points.size() < 2) {
            return Optional.empty();
        }
        try {
            // Geoapify waypoints: "lat,lng|lat,lng|..." (lat first).
            StringBuilder wp = new StringBuilder();
            for (int i = 0; i < points.size(); i++) {
                if (i > 0) wp.append('|');
                wp.append(points.get(i)[0]).append(',').append(points.get(i)[1]);
            }
            java.net.URI uri = UriComponentsBuilder.fromUriString(routingUrl)
                    .queryParam("waypoints", wp.toString())
                    .queryParam("mode", "drive")
                    .queryParam("apiKey", apiKey)
                    .build()
                    .encode()
                    .toUri();
            Map<String, Object> body = client.get().uri(uri).retrieve().body(GEOJSON_TYPE);
            return parse(body);
        } catch (Exception e) {
            log.warn("Geoapify multi-waypoint routing failed ({} points): {}",
                    points.size(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Pulls distance (m) + time (s) out of {@code features[0].properties}. */
    @SuppressWarnings("unchecked")
    private Optional<RouteResult> parse(Map<String, Object> body) {
        if (body == null) {
            return Optional.empty();
        }
        Object featuresObj = body.get("features");
        if (!(featuresObj instanceof List<?> features) || features.isEmpty()) {
            return Optional.empty();
        }
        Object firstObj = features.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            return Optional.empty();
        }
        Object propsObj = ((Map<String, Object>) first).get("properties");
        if (!(propsObj instanceof Map<?, ?> props)) {
            return Optional.empty();
        }
        Object distance = ((Map<String, Object>) props).get("distance"); // meters
        Object time = ((Map<String, Object>) props).get("time");         // seconds
        if (!(distance instanceof Number distanceM) || !(time instanceof Number timeS)) {
            return Optional.empty();
        }
        double distanceKm = distanceM.doubleValue() / 1000.0;
        int durationMin = (int) Math.round(timeS.doubleValue() / 60.0);
        return Optional.of(new RouteResult(distanceKm, durationMin));
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> GEOJSON_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {};
}
