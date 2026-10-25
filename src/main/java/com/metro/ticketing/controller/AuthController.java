package com.metro.ticketing.controller;

import com.metro.ticketing.entity.Role;
import com.metro.ticketing.entity.Station;
import com.metro.ticketing.entity.User;
import com.metro.ticketing.repository.StationRepository;
import com.metro.ticketing.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          StationRepository stationRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/passenger/register")
    @Transactional
    public ResponseEntity<User> registerPassenger(@Valid @RequestBody User request) {
        if (userRepository.findByContact(request.getContact()).isPresent()) {
            throw new IllegalArgumentException("User with this contact already exists.");
        }

        User user = new User();
        user.setUserId("PASS-" + UUID.randomUUID());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setContact(request.getContact());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.PASSENGER);
        user.setRegistrationDate(LocalDate.now());
        user.setIsActive(true);

        return ResponseEntity.ok(userRepository.save(user));
    }

    @PostMapping("/employee/hire")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<User> hireEmployee(@Valid @RequestBody User request) {
        if (userRepository.findByContact(request.getContact()).isPresent()) {
            throw new IllegalArgumentException("User with this contact already exists.");
        }
        if (request.getRole() == null) {
            throw new IllegalArgumentException("Role is required.");
        }
        if (request.getStationId() == null) {
            throw new IllegalArgumentException("Station is required.");
        }

        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new IllegalArgumentException("Station not found."));

        User user = new User();
        user.setUserId("EMP-" + UUID.randomUUID());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setContact(request.getContact());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setHireDate(LocalDate.now());
        user.setRegistrationDate(LocalDate.now());
        user.setStation(station);
        user.setIsActive(true);

        return ResponseEntity.ok(userRepository.save(user));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        User user = userRepository.findByContact(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Current user not found"));
        return ResponseEntity.ok(user);
    }
}
