package com.metro.ticketing.repository;

import com.metro.ticketing.entity.MetroCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetroCardRepository extends JpaRepository<MetroCard, String> {
    List<MetroCard> findByPassenger_UserId(String passengerId);
    Optional<MetroCard> findByPassenger_UserIdAndIsActiveTrue(String passengerId);
}