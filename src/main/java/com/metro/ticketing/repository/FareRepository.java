package com.metro.ticketing.repository;

import com.metro.ticketing.entity.Fare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FareRepository extends JpaRepository<Fare, String> {

    boolean existsBySourceStation_StationIdAndDestStation_StationId(
            String sourceStationId, String destStationId);

    boolean existsBySourceStation_StationIdAndDestStation_StationIdAndFareIdNot(
            String sourceStationId, String destStationId, String fareId);

    Optional<Fare> findBySourceStation_StationIdAndDestStation_StationId(
            String sourceStationId, String destStationId);
}
