package com.saferide.monolith.rides.model.dtos;

/**
 * Shown to the HOST before accepting a join request: what they pay now, what
 * they'd pay after accepting (never above their solo fare, thanks to the
 * cap), what the requester would pay, and the new trip metrics.
 */
public record HostAcceptFarePreviewResponse(
        double yourShareNow,
        double yourShareAfter,
        double requesterShare,
        double grossAfter,
        double tripKmAfter,
        int tripMinAfter,
        String currency
) {
}
