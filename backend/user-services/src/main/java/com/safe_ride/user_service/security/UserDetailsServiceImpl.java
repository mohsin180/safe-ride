package com.safe_ride.user_service.security;

import com.safe_ride.user_service.model.Users;
import com.safe_ride.user_service.repos.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository repository;

    public UserDetailsServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        Users users = repository.findByEmail(email).orElseThrow(
                () -> new UsernameNotFoundException("User not Found")
        );
        List<SimpleGrantedAuthority> authorities = users.getRole() != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + users.getRole().name()))
                : Collections.emptyList();

        return new org.springframework.security.core.userdetails.User(
                users.getEmail(),
                users.getPassword(),
                users.isEnabled(),
                true, true, true,
                authorities
        );
    }
}
