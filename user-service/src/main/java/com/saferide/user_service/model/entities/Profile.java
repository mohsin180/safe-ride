package com.saferide.user_service.model.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String fullName;

    private String profilePicture;
    @Column(nullable = false)
    private Double rating = 0.0;
    @JoinColumn(nullable = false, name = "user-id", unique = true)
    @OneToOne(fetch = FetchType.LAZY)
    private Users users;
    @CreatedDate
    private LocalDateTime createdAt;
}
