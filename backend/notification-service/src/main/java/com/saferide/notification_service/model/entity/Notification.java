package com.saferide.notification_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single in-app notification for one recipient. Ride events fan out into
 * one row per recipient so each user reads their own list.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_user_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    /** REQUEST | TRIP | RATING | SYSTEM — drives the icon on the client. */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** The ride this notification is about, when applicable. */
    private UUID rideId;

    /** For an actionable RATING prompt: who to rate (a co-passenger who left). */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    /** Display name of {@link #subjectUserId}, for the rating sheet / request card. */
    @Column(name = "subject_name")
    private String subjectName;

    /** Average rating of the subject — shown on the join-request card. */
    @Column(name = "subject_rating")
    private Double subjectRating;

    /** For a JOIN_REQUEST: the request to accept/decline + the requester's route. */
    @Column(name = "request_id")
    private UUID requestId;

    private String pickup;

    private String drop;

    @Column(nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
