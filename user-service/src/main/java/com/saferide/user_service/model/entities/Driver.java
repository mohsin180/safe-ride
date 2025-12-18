package com.saferide.user_service.model.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false)
    private String vehicleNo;
    @Column(nullable = false)
    private String vehicleModel;
    @OneToOne
    private Users users;
}
