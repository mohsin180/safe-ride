package com.safe_ride.rides_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized fare-pricing constants, bound from the {@code pricing.*} block in
 * config-server's {@code rides-service.yaml} (each value env-var-backed). Keeping
 * them here means the pricing model is fully config-driven instead of hardcoded
 * in {@link com.safe_ride.rides_service.service.PriceService}.
 */
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    /** Flat pickup charge applied to every trip. */
    private double baseFare = 50.0;
    /** Charge per km of road distance. */
    private double perKm = 20.0;
    /** Charge per minute of trip duration. */
    private double perMin = 2.0;
    /** Fraction (0..1) knocked off the gross when a ride is shared (>1 seat). */
    private double sharedDiscountRate = 0.20;
    /** Gross multiplier applied to PREMIUM rides. */
    private double premiumMultiplier = 1.5;
    /** Floor the gross trip fare never drops below. */
    private double minFare = 80.0;
    /** ISO-ish currency label echoed back in fare DTOs. */
    private String currency = "PKR";

    public double getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(double baseFare) {
        this.baseFare = baseFare;
    }

    public double getPerKm() {
        return perKm;
    }

    public void setPerKm(double perKm) {
        this.perKm = perKm;
    }

    public double getPerMin() {
        return perMin;
    }

    public void setPerMin(double perMin) {
        this.perMin = perMin;
    }

    public double getSharedDiscountRate() {
        return sharedDiscountRate;
    }

    public void setSharedDiscountRate(double sharedDiscountRate) {
        this.sharedDiscountRate = sharedDiscountRate;
    }

    public double getPremiumMultiplier() {
        return premiumMultiplier;
    }

    public void setPremiumMultiplier(double premiumMultiplier) {
        this.premiumMultiplier = premiumMultiplier;
    }

    public double getMinFare() {
        return minFare;
    }

    public void setMinFare(double minFare) {
        this.minFare = minFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
