package com.safe_ride.rides_service.service;

import com.safe_ride.rides_service.config.UserContext;
import com.safe_ride.rides_service.exceptions.RoleNotAllowedException;
import com.safe_ride.rides_service.model.dtos.CreateRideRequest;
import com.safe_ride.rides_service.model.dtos.RideResponse;
import com.safe_ride.rides_service.model.dtos.RideStatsResponse;
import com.safe_ride.rides_service.model.entity.Gender;
import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideStatus;
import com.safe_ride.rides_service.model.mapper.RideMapper;
import com.safe_ride.rides_service.repo.RideRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class RideService {
    private final RideRepository rideRepository;
    private final RideMapper rideMapper;

    public RideService(RideRepository rideRepository, RideMapper rideMapper) {
        this.rideRepository = rideRepository;
        this.rideMapper = rideMapper;
    }

    public RideResponse createRide(CreateRideRequest request) {
        UserContext ctx = getCurrentUserContext();
        if (!"PASSENGER".equals(ctx.role())) {
            throw new RoleNotAllowedException("Only passengers can request a ride.");
        }

        Ride ride = rideMapper.toRide(request);
        ride.setCreatedByUserId(ctx.userId());
        ride.setGender(Gender.valueOf(ctx.gender()));
        ride.setStatus(RideStatus.PENDING);

        return rideMapper.toResponse(rideRepository.save(ride));
    }

    public RideStatsResponse getMyStats() {
        UserContext ctx = getCurrentUserContext();
        long trips = "DRIVER".equals(ctx.role())
                ? rideRepository.countByDriverIdAndStatus(ctx.userId(), RideStatus.COMPLETED)
                : rideRepository.countByCreatedByUserIdAndStatus(ctx.userId(), RideStatus.COMPLETED);
        return new RideStatsResponse(trips, null);
    }

    private UserContext getCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return (UserContext) authentication.getDetails();
    }
}