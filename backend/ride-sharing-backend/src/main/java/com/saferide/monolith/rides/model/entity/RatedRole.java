package com.saferide.monolith.rides.model.entity;

/** Which side of a ride a rating was given to — decides which profile
 *  (driver vs passenger) gets its aggregate rating updated. */
public enum RatedRole {
    DRIVER,
    PASSENGER
}
