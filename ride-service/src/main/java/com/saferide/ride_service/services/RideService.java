package com.saferide.ride_service.services;

import com.saferide.ride_service.model.dtos.RideMapper;
import com.saferide.ride_service.model.dtos.RideRequest;
import com.saferide.ride_service.model.dtos.RideResponse;
import com.saferide.ride_service.model.entity.Ride;
import com.saferide.ride_service.repo.RideRepository;
import org.springframework.stereotype.Service;

@Service
public class RideService {
    private final RideRepository repository;
    private final RideMapper mapper;
    private final PricingService pricingService;

    public RideService(RideRepository repository, RideMapper mapper, PricingService pricingService) {
        this.repository = repository;
        this.mapper = mapper;
        this.pricingService = pricingService;
    }

    public RideResponse createRide(RideRequest request, String userId) {
        Ride ride = mapper.toRide(request);
        double price = pricingService.calculatePrice(request.pickupLat(), request.pickupLon(),
                request.dropOffLat(), request.dropOffLon());
        ride.setPrice(price);
        repository.save(ride);
        return mapper.toRideResponse(ride);
    }
}
